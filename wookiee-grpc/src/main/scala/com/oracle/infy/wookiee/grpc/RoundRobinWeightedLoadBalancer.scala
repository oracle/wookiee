package com.oracle.infy.wookiee.grpc
import java.util
import java.util.List
import java.util.concurrent.atomic.{AtomicBoolean, AtomicIntegerFieldUpdater, AtomicReference}
import java.util.function.UnaryOperator

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.{MoreObjects, Objects, Preconditions}
import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.{EmptyPicker, ReadyPicker, Ref}
import io.grpc.ConnectivityState._
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.util.ForwardingSubchannel
import io.grpc.{Attributes, ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}

import scala.jdk.CollectionConverters._
import scala.util.Random

class RoundRobinWeightedLoadBalancer(helper: LoadBalancer.Helper) extends LoadBalancer {

  // For pickfirst loadbalancer, not round robin.
  //val maybeSubChannel: AtomicReference[Option[Subchannel]] = new AtomicReference[Option[Subchannel]](None)

  // Round robin loadbalancer data members.
  private[wookiee] val STATE_INFO: Attributes.Key[Ref[ConnectivityStateInfo]] = Attributes.Key.create("state-info")

  val subChannels: util.HashMap[EquivalentAddressGroup, Subchannel] =
    new util.HashMap[EquivalentAddressGroup, Subchannel]()

  //TODO: verify this is correct
  private val eitherPicker: AtomicReference[Either[ReadyPicker, EmptyPicker]] =
    new AtomicReference[Either[ReadyPicker, EmptyPicker]](Right(EmptyPicker(EMPTY_OK)))

  val random: Random = new Random()
  val currentState: AtomicReference[ConnectivityState] = new AtomicReference[ConnectivityState]()

  override def handleNameResolutionError(error: Status): Unit = {
    /*maybeSubChannel.get() match {
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
    }*/
    eitherPicker.get() match {
      case Left(readyPicker) => helper.updateBalancingState(TRANSIENT_FAILURE, readyPicker)
      case Right(_) => helper.updateBalancingState(TRANSIENT_FAILURE, EmptyPicker(error))
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
          case _ => val subchannelAttrs = Attributes.
            newBuilder
            .set(STATE_INFO, new RoundRobinWeightedLoadBalancer.Ref[ConnectivityStateInfo](ConnectivityStateInfo.forNonError(IDLE)))

            val subchannel = checkNotNull(helper.createSubchannel(CreateSubchannelArgs.newBuilder.setAddresses(originalAddressGroup).setAttributes(subchannelAttrs.build).build), "subchannel")
            subchannel.start(new LoadBalancer.SubchannelStateListener() {
              override def onSubchannelState(state: ConnectivityStateInfo): Unit = {
                processSubchannelState(subchannel, state)
              }
            })
            subChannels.put(strippedAddressGroup, subchannel)
            subchannel.requestConnection()
        }
      })
    val removedSubchannels: util.ArrayList[Subchannel] = new util.ArrayList[Subchannel]()
    removedAddrs.forEach(addressGroup => {
      removedSubchannels.add(subChannels.remove(addressGroup))
    })
    updateBalancingState()
    removedSubchannels.forEach(removedSubchannel => shutdownSubchannel(removedSubchannel))
  }

  private def shutdownSubchannel(subchannel: LoadBalancer.Subchannel): Unit = {
    subchannel.shutdown()
    getSubchannelStateInfoRef(subchannel).value.getAndSet(ConnectivityStateInfo.forNonError(SHUTDOWN))
  }

  private val EMPTY_OK = Status.OK.withDescription("no subchannels ready")

  private def updateBalancingState(): Unit = {
    val activeList: util.List[LoadBalancer.Subchannel] = filterNonFailingSubchannels(subChannels.values())
    subChannels.values.forEach(subchannel => {}) //filterNonFailingSubchannels(getSubchannels)
    if (activeList.isEmpty) { // No READY subchannels, determine aggregate state and error status
      val isConnecting: AtomicBoolean = new AtomicBoolean(false)
      val aggStatus: AtomicReference[Status] = new AtomicReference[Status](EMPTY_OK)
      subChannels
        .values
        .forEach(subchannel => {
          val stateInfo: ConnectivityStateInfo = getSubchannelStateInfoRef(subchannel).value
          // This subchannel IDLE is not because of channel IDLE_TIMEOUT,
          // in which case LB is already shutdown.
          // RRLB will request connection immediately on subchannel IDLE.
          if ((stateInfo.getState eq CONNECTING) || (stateInfo.getState eq IDLE)) {
            isConnecting.getAndSet(true)
          }
          if ((aggStatus eq EMPTY_OK) || !aggStatus.get.isOk) {
            aggStatus.updateAndGet(new UnaryOperator[Status](stateInfo.getStatus) {
              override def apply(t: Status): Status = t
            })
          }
        })
      // Set the connectivity state based on whether subchannel is connecting or not.
      val state: ConnectivityState = if (isConnecting.get) CONNECTING else TRANSIENT_FAILURE
      updateBalancingState(
        state,
        // If all subchannels are TRANSIENT_FAILURE, return the Status associated with
        // an arbitrary subchannel, otherwise return OK.
        new RoundRobinWeightedLoadBalancer.RequestConnectionPicker(aggStatus.get())
      )
    } else { // initialize the Picker to a random start index to ensure that a high frequency of Picker
      // churn does not skew subchannel selection.
      val startIndex: Int = random.nextInt(activeList.size)
      updateBalancingState(READY, new RoundRobinWeightedLoadBalancer.RequestConnectionPicker(activeList, startIndex))
    }
  }

  private def updateBalancingState(state: ConnectivityState, picker: RoundRobinWeightedLoadBalancer.RequestConnectionPicker) = {

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

  // package-private to avoid synthetic access
  private[wookiee] def isReady(subchannel: Subchannel) = {
    val maybeStateVal: Ref[ConnectivityStateInfo] = getSubchannelStateInfoRef(subchannel)
    maybeStateVal.value.get.getState == READY
  }

  // Gets subchannel's state info reference, if reference is null a null pointer exception is thrown.
  // Otherwise, the reference is returned to the calling method.
  private def getSubchannelStateInfoRef(subchannel: LoadBalancer.Subchannel): Ref[ConnectivityStateInfo] = {
    val maybeStateVal: Option[Ref[ConnectivityStateInfo]] = Option(subchannel.getAttributes.get(STATE_INFO))
    maybeStateVal.get match {
      case stateVal: ForwardingSubchannel => stateVal
      case _ =>
        throw new NullPointerException("STATE_INFO") // TODO: is there something else to do here instead of throwing exception?
    }
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

  /*private def processSubchannelState(subchannel: LoadBalancer.Subchannel, stateInfo: ConnectivityStateInfo): Unit = {
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
  }*/

  private def processSubchannelState(subchannel: Subchannel, connectivityStateInfo: ConnectivityStateInfo) = {
    if (subChannels.get(stripAttrs(subchannel.getAddresses)) ne subchannel) {
      return
    }
    if (connectivityStateInfo.getState eq IDLE) {
      subchannel.requestConnection()
    }
    val subchannelStateRef: RoundRobinWeightedLoadBalancer.Ref[ConnectivityStateInfo] = getSubchannelStateInfoRef(subchannel)
    if (subchannelStateRef.value.get.getState == TRANSIENT_FAILURE) {
      if (connectivityStateInfo.getState == CONNECTING || connectivityStateInfo.getState == IDLE) {
        return
      }
    }
    subchannelStateRef.value.updateAndGet(connectivityStateInfo)
    updateBalancingState()
  }

  /**
    * Converts list of {@link EquivalentAddressGroup} to {@link EquivalentAddressGroup} set and
    * remove all attributes. The values are the original EAGs.
    */
  private def stripAttrs(groupList: util.List[EquivalentAddressGroup]) = {
    val addrs = new util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup](groupList.size * 2)
    groupList.forEach(group => addrs.put(stripAttrs(group), group))
    addrs
  }

  private def stripAttrs(eag: EquivalentAddressGroup) = new EquivalentAddressGroup(eag.getAddresses)

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

  /*object RequestConnectionPicker {

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

    def apply(status: Status): RequestConnectionPicker = {
      RequestConnectionPicker(status)
    }
  }*/

  object RoundRobinWeightedPicker {

    def apply(
               list: util.List[Subchannel],
               startIndex: Int
             ): ReadyPicker = {
      ReadyPicker(
        list: util.List[Subchannel],
        startIndex: Int
      )
    }

    def apply(status: Status): EmptyPicker = {
      checkNotNull(status, "STATUS")
      EmptyPicker(status)
    }
  }

  //TODO: this is where picking (weighted round robin) logic should happen (possibly ?)
/*case class RequestConnectionPicker(
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
        case _                                   => throw new IllegalArgumentException("Unsupported state:" + stateInfo)
      }

    }

    private[wookiee] def isEquivalentTo(picker: RoundRobinWeightedLoadBalancer.RequestConnectionPicker): Boolean = {
      if (!picker.isInstanceOf[RoundRobinWeightedLoadBalancer.RequestConnectionPicker]) {
        return false
      }
      val other: RoundRobinWeightedLoadBalancer.RequestConnectionPicker =
        picker.asInstanceOf[RoundRobinWeightedLoadBalancer.RequestConnectionPicker]
      // the lists cannot contain duplicate subchannels
      (other eq this) || (list.size == other.list.size && new HashSet[LoadBalancer.Subchannel](list)
        .containsAll(other.list))
    }
  }*/

  abstract case class RoundRobinWeightedPicker() extends SubchannelPicker {
    abstract def isEquivalentTo (picker: RoundRobinWeightedLoadBalancer.RoundRobinWeightedPicker): Boolean
  }

  case class ReadyPicker(list: util.List[Subchannel], startIndex: Int) extends RoundRobinWeightedPicker {
    private val indexUpdater: AtomicIntegerFieldUpdater[ReadyPicker] =
      AtomicIntegerFieldUpdater.newUpdater(classOf[RoundRobinWeightedLoadBalancer.ReadyPicker], "index")

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult = PickResult.withSubchannel(nextSubchannel)

    override def toString: String = MoreObjects.toStringHelper(classOf[RoundRobinWeightedLoadBalancer.ReadyPicker]).add("list", list).toString

    private def nextSubchannel = {
      val size = list.size()
      val currentIndex = indexUpdater.incrementAndGet(this)
      if (currentIndex >= size) {
        val newIndex = currentIndex % size
        indexUpdater.compareAndSet(this, currentIndex, newIndex)
        list.get(newIndex)
      }
      else {
        list.get(currentIndex)
      }
    }

    private[wookiee] def getList = list

    override private[wookiee] def isEquivalentTo(picker: RoundRobinWeightedLoadBalancer.RoundRobinWeightedPicker): Boolean = {
      if (!picker.isInstanceOf[RoundRobinWeightedLoadBalancer.ReadyPicker]) return false
      val other = picker.asInstanceOf[RoundRobinWeightedLoadBalancer.ReadyPicker]
      // the lists cannot contain duplicate subchannels
      (other eq this) || (list.size == other.list.size && new util.HashSet[LoadBalancer.Subchannel](list).containsAll(other.list))
    }
  }

  case class EmptyPicker(status: Status) extends RoundRobinWeightedPicker {

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult = {
      if (status.isOk) PickResult.withNoResult
      else PickResult.withError(status)
    }

    override private[util] def isEquivalentTo(picker: RoundRobinWeightedLoadBalancer.RoundRobinWeightedPicker) =
      picker.isInstanceOf[RoundRobinWeightedLoadBalancer.EmptyPicker] && (Objects.equal(status, picker.asInstanceOf[RoundRobinWeightedLoadBalancer.EmptyPicker].status) || (status.isOk && picker.asInstanceOf[RoundRobinWeightedLoadBalancer.EmptyPicker].status.isOk))

    override def toString: String = MoreObjects.toStringHelper(classOf[RoundRobinWeightedLoadBalancer.EmptyPicker]).add("status", status).toString
  }

  class Ref[A](newVal: A) {
    val value: AtomicReference[A] = new AtomicReference[A](newVal)
  }
}
