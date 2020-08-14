package com.oracle.infy.wookiee.grpc
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.{HashSet, List}

import com.google.common.base.Preconditions.checkNotNull
import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.RequestConnectionPicker
import io.grpc.ConnectivityState._
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.util.ForwardingSubchannel
import io.grpc.{Attributes, ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}

import scala.util.Random

class RoundRobinWeightedLoadBalancer(helper: LoadBalancer.Helper) extends LoadBalancer {

  // For pickfirst loadbalancer, not round robin.
  val maybeSubChannel: AtomicReference[Option[Subchannel]] = new AtomicReference[Option[Subchannel]](None)

  // Round robin loadbalancer variables.
  private[util] val STATE_INFO = Attributes.Key.create("state-info")

  val subChannels: util.HashMap[EquivalentAddressGroup, Subchannel] =
    new util.HashMap[EquivalentAddressGroup, Subchannel]()

  private val maybePicker: AtomicReference[Option[RequestConnectionPicker]] =
    new AtomicReference[Option[RequestConnectionPicker]](None)
  val random: Random
  val currentState: AtomicReference[ConnectivityState] = new AtomicReference[ConnectivityState]()

  override def handleNameResolutionError(error: Status): Unit = {
    maybeSubChannel.get() match {
      case Some(subchannel) =>
        subchannel.shutdown()
        helper.updateBalancingState(
          TRANSIENT_FAILURE,
          RequestConnectionPicker(Some(subchannel), helper, RoundRobinWeightedLoadBalancer.TRANSIENT_FAILURE)
        )
      case None =>
        helper.updateBalancingState(
          TRANSIENT_FAILURE,
          RequestConnectionPicker(None, helper, RoundRobinWeightedLoadBalancer.TRANSIENT_FAILURE)
        )
    }
  }

  override def handleResolvedAddresses(resolvedAddresses: LoadBalancer.ResolvedAddresses): Unit = {
    val servers: java.util.List[EquivalentAddressGroup] = resolvedAddresses.getAddresses
    val currentAddrs: util.Set[EquivalentAddressGroup] = subChannels.keySet
    val latestAddrs: util.Map[EquivalentAddressGroup, EquivalentAddressGroup] = stripAttrs(servers)
    val removedAddrs: util.Set[EquivalentAddressGroup] = setsDifference(currentAddrs, latestAddrs.keySet)

    // This is for pickFirstLoadBalancer, not round robin.
    /*maybeSubChannel.get() match {
      case Some(subchannel) => subchannel.updateAddresses(servers)
      case None =>
        val subchannel: LoadBalancer.Subchannel =
          helper.createSubchannel(CreateSubchannelArgs.newBuilder.setAddresses(servers).build)
        subchannel.start(new LoadBalancer.SubchannelStateListener() {
          override def onSubchannelState(stateInfo: ConnectivityStateInfo): Unit = {
            processSubchannelState(subchannel, stateInfo)
          }
        })
        maybeSubChannel.updateAndGet(new UnaryOperator[Option[Subchannel]] {
          override def apply(t: Option[Subchannel]): Option[Subchannel] = {
            val _ = t
            Some(subchannel)
          }
        })
        helper.updateBalancingState(
          CONNECTING,
          RequestConnectionPicker(Some(subchannel), helper, RoundRobinWeightedLoadBalancer.CONNECTING)
        )
        subchannel.requestConnection()
    }*/
    latestAddrs
      .entrySet()
      .forEach(latestEntry => {
        val strippedAddressGroup: EquivalentAddressGroup = latestEntry.getKey
        val originalAddressGroup: EquivalentAddressGroup = latestEntry.getValue
        val existingSubchannel: Option[Subchannel] = Option(subChannels.get(strippedAddressGroup))
        existingSubchannel.get match {
          case subchannel: ForwardingSubchannel =>
            subchannel.updateAddresses(originalAddressGroup.asInstanceOf[java.util.List[EquivalentAddressGroup]])
          case _ => connectionRequest(originalAddressGroup, strippedAddressGroup)
        }
      })
    val removedSubchannels: util.ArrayList[Subchannel] = new util.ArrayList[Subchannel]()
    removedAddrs.forEach(addressGroup => {
      removedSubchannels.add(subChannels.remove(addressGroup))
    })
  }

  private val EMPTY_OK = Status.OK.withDescription("no subchannels ready")

  private def updateBalancingState(
      state: ConnectivityState,
      picker: RoundRobinWeightedLoadBalancer.RequestConnectionPicker
  ): Unit = {
    val activeList: util.List[LoadBalancer.Subchannel] = filterNonFailingSubchannels(subChannels.values())
    subChannels.values.forEach(subchannel => {}) //filterNonFailingSubchannels(getSubchannels)
    if (activeList.isEmpty) { // No READY subchannels, determine aggregate state and error status
      var isConnecting: Boolean = false
      var aggStatus: Status = EMPTY_OK
      for (subchannel <- getSubchannels) {
        val stateInfo: ConnectivityStateInfo = getSubchannelStateInfoRef(subchannel).value
        // This subchannel IDLE is not because of channel IDLE_TIMEOUT,
        // in which case LB is already shutdown.
        // RRLB will request connection immediately on subchannel IDLE.
        if ((stateInfo.getState eq CONNECTING) || (stateInfo.getState eq IDLE)) {
          isConnecting = true
        }
        if ((aggStatus eq EMPTY_OK) || !(aggStatus.isOk)) {
          aggStatus = stateInfo.getStatus
        }
      }
      updateBalancingState(
        if (isConnecting) {
          CONNECTING
        } else {
          TRANSIENT_FAILURE
        }, // If all subchannels are TRANSIENT_FAILURE, return the Status associated with
        // an arbitrary subchannel, otherwise return OK.
        new RoundRobinLoadBalancer.EmptyPicker(aggStatus)
      )
    } else { // initialize the Picker to a random start index to ensure that a high frequency of Picker
      // churn does not skew subchannel selection.
      val startIndex: Int = random.nextInt(activeList.size)
      updateBalancingState(READY, new RoundRobinLoadBalancer.ReadyPicker(activeList, startIndex))
    }
  }

  /**
    * Filters out non-ready subchannels.
    */
  private def filterNonFailingSubchannels(subchannels: util.Collection[LoadBalancer.Subchannel]) = {
    val readySubchannels = new util.ArrayList[LoadBalancer.Subchannel](subchannels.size)
    subchannels.forEach(subchannel => {
      if (isReady(subchannel)) readySubchannels.add(subchannel)
    })
    readySubchannels
  }

  private def getSubchannelStateInfoRef(subchannel: LoadBalancer.Subchannel) =
    checkNotNull(subchannel.getAttributes.get(STATE_INFO), "STATE_INFO")

  // package-private to avoid synthetic access
  private[util] def isReady(subchannel: LoadBalancer.Subchannel) = {
    getSubchannelStateInfoRef(subchannel).value.getState eq READY
  }

  private def connectionRequest(
      originalAddressGroup: EquivalentAddressGroup,
      strippedAddressGroups: EquivalentAddressGroup
  ): Unit = {
    val subchannelAttrs = Attributes
      .newBuilder()
      .set(STATE_INFO, new AtomicReference[ConnectivityStateInfo](ConnectivityStateInfo.forNonError(IDLE)))
    val subchannel = checkNotNull(
      helper.createSubchannel(
        CreateSubchannelArgs.newBuilder.setAddresses(originalAddressGroup).setAttributes(subchannelAttrs.build).build
      ),
      "subchannel"
    )
    subchannel.start(new LoadBalancer.SubchannelStateListener() {
      override def onSubchannelState(state: ConnectivityStateInfo): Unit = {
        processSubchannelState(subchannel, state)
      }
    })
    subChannels.put(strippedAddressGroups, subchannel)
    subchannel.requestConnection()
  }

  override def shutdown(): Unit = {
    maybeSubChannel.get().foreach(_.shutdown())
  }

  private def processSubchannelState(subchannel: LoadBalancer.Subchannel, stateInfo: ConnectivityStateInfo): Unit = {
    val currentState = stateInfo.getState
    if (currentState eq SHUTDOWN) return
    val picker: SubchannelPicker = currentState match {
      case IDLE =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          Some(subchannel),
          helper,
          RoundRobinWeightedLoadBalancer.IDLE,
          stateInfo
        )

      case CONNECTING =>
        // It's safe to use RequestConnectionPicker here, so when coming from IDLE we could leave
        // the current picker in-place. But ignoring the potential optimization is simpler.
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          Some(subchannel),
          helper,
          RoundRobinWeightedLoadBalancer.CONNECTING,
          stateInfo
        )

      case READY =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          Some(subchannel),
          helper,
          RoundRobinWeightedLoadBalancer.READY,
          stateInfo
        )

      case TRANSIENT_FAILURE =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          Some(subchannel),
          helper,
          RoundRobinWeightedLoadBalancer.TRANSIENT_FAILURE,
          stateInfo
        )

      case _ =>
        throw new IllegalArgumentException("Unsupported state:" + currentState)
    }
    helper.updateBalancingState(currentState, picker)
  }

  /**
    * Converts list of {@link EquivalentAddressGroup} to {@link EquivalentAddressGroup} set and
    * remove all attributes. The values are the original EAGs.
    */
  private def stripAttrs(groupList: util.List[EquivalentAddressGroup]) = {
    val addrs = new util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup](groupList.size * 2)
    groupList.forEach(_ => addrs.put(stripAttrs(_), _))
    addrs
  }

  private def setsDifference[T](a: util.Set[T], b: util.Set[T]) = {
    val aCopy = new util.HashSet[T](a)
    aCopy.removeAll(b)
    aCopy
  }

}

object RoundRobinWeightedLoadBalancer {
  sealed trait ConnectionState
  case object IDLE extends ConnectionState
  case object CONNECTING extends ConnectionState
  case object READY extends ConnectionState
  case object TRANSIENT_FAILURE extends ConnectionState
  val maybeList: AtomicReference[Option[List[Subchannel]]] = new AtomicReference[Option[List[Subchannel]]]()

  object RequestConnectionPicker {

    def apply(
        subchannel: Option[Subchannel],
        helper: LoadBalancer.Helper,
        connectionState: ConnectionState
    ): RequestConnectionPicker = {
      RequestConnectionPicker(
        subchannel,
        helper,
        connectionState,
        ConnectivityStateInfo.forNonError(ConnectivityState.READY)
      )
    }
  }

  //TODO: this is where picking (weighted round robin) logic should happen (possibly ?)
  case class RequestConnectionPicker(
      maybeSubChannel: Option[Subchannel],
      helper: LoadBalancer.Helper,
      connectionState: ConnectionState,
      stateInfo: ConnectivityStateInfo
  ) extends SubchannelPicker {
    val requestedConnection = new AtomicBoolean(false)
    val list: util.List[Subchannel]

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): PickResult = {
      (connectionState, maybeSubChannel) match {
        case (IDLE, Some(subchannel)) =>
          if (requestedConnection.compareAndSet(false, true)) {
            helper
              .getSynchronizationContext
              .execute(new Runnable {
                override def run(): Unit = {
                  subchannel.requestConnection()
                }
              })
          }
          PickResult.withNoResult()
        case (CONNECTING, _)                       => PickResult.withNoResult
        case (READY, Some(subchannel))             => PickResult.withSubchannel(subchannel)
        case (TRANSIENT_FAILURE | IDLE | READY, _) => PickResult.withError(stateInfo.getStatus)
        case (_)                                   => throw new IllegalArgumentException("Unsupported state:" + stateInfo)
      }

    }

    private[util] def isEquivalentTo(picker: RoundRobinWeightedLoadBalancer.RequestConnectionPicker): Boolean = {
      if (!((picker.isInstanceOf[RoundRobinWeightedLoadBalancer.RequestConnectionPicker]))) {
        return false
      }
      val other: RoundRobinWeightedLoadBalancer.RequestConnectionPicker =
        picker.asInstanceOf[RoundRobinWeightedLoadBalancer.RequestConnectionPicker]
      // the lists cannot contain duplicate subchannels
      return (other eq this) || (list.size == other.list.size && new HashSet[LoadBalancer.Subchannel](list)
        .containsAll(other.list))
    }
  }

}
