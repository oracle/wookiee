package com.oracle.infy.wookiee.grpc
import java.util.concurrent.atomic.AtomicBoolean

import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.RequestConnectionPicker
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.{ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}
import io.grpc.ConnectivityState._

class RoundRobinWeightedLoadBalancer(helper: LoadBalancer.Helper, subchannel: Subchannel) extends LoadBalancer {

  override def handleNameResolutionError(error: Status): Unit = {
    if (Option(subchannel).nonEmpty) {
      subchannel.shutdown()
    }
    // NB(lukaszx0) Whether we should propagate the error unconditionally is arguable. It's fine
    // for time being.
    helper.updateBalancingState(
      TRANSIENT_FAILURE,
      RequestConnectionPicker(subchannel, helper, RoundRobinWeightedLoadBalancer.CONNECTING)
    )
  }

  override def handleResolvedAddresses(resolvedAddresses: LoadBalancer.ResolvedAddresses): Unit = {
    val servers: java.util.List[EquivalentAddressGroup] = resolvedAddresses.getAddresses
    if (subchannel == null) {
      val subchannel: LoadBalancer.Subchannel =
        helper.createSubchannel(CreateSubchannelArgs.newBuilder.setAddresses(servers).build)
      subchannel.start(new LoadBalancer.SubchannelStateListener() {
        override def onSubchannelState(stateInfo: ConnectivityStateInfo): Unit = {
          processSubchannelState(subchannel, stateInfo)
        }
      })
      // The channel state does not get updated when doing name resolving today, so for the moment
      // let LB report CONNECTION and call subchannel.requestConnection() immediately.
      helper.updateBalancingState(
        CONNECTING,
        RequestConnectionPicker(subchannel, helper, RoundRobinWeightedLoadBalancer.CONNECTING)
      )
      subchannel.requestConnection()
    } else {
      subchannel.updateAddresses(servers)
    }
  }

  override def shutdown(): Unit = {
    if (Option(subchannel).nonEmpty)
      subchannel.shutdown()
  }

  // TODO: override def handleNameResolutionError(Status error): Unit = ???
  private def processSubchannelState(subchannel: LoadBalancer.Subchannel, stateInfo: ConnectivityStateInfo): Unit = {
    val currentState = stateInfo.getState
    if (currentState eq SHUTDOWN) return
    val picker: SubchannelPicker = currentState match {
      case IDLE =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          subchannel,
          helper,
          RoundRobinWeightedLoadBalancer.IDLE,
          stateInfo
        )

      case CONNECTING =>
        // It's safe to use RequestConnectionPicker here, so when coming from IDLE we could leave
        // the current picker in-place. But ignoring the potential optimization is simpler.
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          subchannel,
          helper,
          RoundRobinWeightedLoadBalancer.CONNECTING,
          stateInfo
        )

      case READY =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          subchannel,
          helper,
          RoundRobinWeightedLoadBalancer.READY,
          stateInfo
        )

      case TRANSIENT_FAILURE =>
        RoundRobinWeightedLoadBalancer.RequestConnectionPicker(
          subchannel,
          helper,
          RoundRobinWeightedLoadBalancer.TRANSIENT_FAILURE,
          stateInfo
        )

      case _ =>
        throw new IllegalArgumentException("Unsupported state:" + currentState)
    }
    helper.updateBalancingState(currentState, picker)
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
        subchannel: Subchannel,
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
      subchannel: Subchannel,
      helper: LoadBalancer.Helper,
      connectionState: ConnectionState,
      stateInfo: ConnectivityStateInfo
  ) extends SubchannelPicker {
    val requestedConnection = new AtomicBoolean(false)

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): PickResult = {
      connectionState match {
        case IDLE =>
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
        case CONNECTING        => PickResult.withNoResult
        case READY             => PickResult.withSubchannel(subchannel)
        case TRANSIENT_FAILURE => PickResult.withError(stateInfo.getStatus)
      }

    }
  }
}
