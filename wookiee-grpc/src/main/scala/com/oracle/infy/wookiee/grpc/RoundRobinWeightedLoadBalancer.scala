package com.oracle.infy.wookiee.grpc
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.function.UnaryOperator

import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.RequestConnectionPicker
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.{Attributes, ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}
import io.grpc.ConnectivityState._
import io.grpc.internal.GrpcAttributes
import io.grpc.util.ForwardingSubchannel

class RoundRobinWeightedLoadBalancer(helper: LoadBalancer.Helper) extends LoadBalancer {

  val maybeSubChannel: AtomicReference[Option[Subchannel]] = new AtomicReference[Option[Subchannel]](None)
  // TODO: not sure which one of these is better
  //val maybeSubChannels: AtomicReference[Option[util.HashMap[EquivalentAddressGroup, Subchannel]]] = new AtomicReference[Option[util.HashMap[EquivalentAddressGroup, Subchannel]]](None)
  val maybeSubChannels: util.HashMap[EquivalentAddressGroup, Subchannel] = new util.HashMap[EquivalentAddressGroup, Subchannel]()

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
    val attributes: Attributes = resolvedAddresses.getAttributes
    val currentAddrs: util.Set[EquivalentAddressGroup] = maybeSubChannels.keySet
    val latestAddrs: util.Map[EquivalentAddressGroup, EquivalentAddressGroup] = stripAttrs(servers)
    val removedAddrs: util.Set[EquivalentAddressGroup] = setsDifference(currentAddrs, latestAddrs.keySet)

    //val serviceConfig: util.Map[Option[String]] = attributes.get(GrpcAttributes.NAME_RESOLVER_SERVICE_CONFIG)
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
    latestAddrs.entrySet().forEach(latestEntry => {
      val strippedAddressGroup: EquivalentAddressGroup = latestEntry.getKey
      val originalAddressGroup: EquivalentAddressGroup = latestEntry.getValue
      val existingSubchannel: Option[Subchannel] = Option(maybeSubChannels.get(strippedAddressGroup))
      existingSubchannel.get match {
        case subchannel: ForwardingSubchannel =>
        case _ =>
      }
    })
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
  }
}
