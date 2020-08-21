package com.oracle.infy.wookiee.grpc
import java.util
import java.util.List
import java.util.concurrent.atomic.{AtomicBoolean, AtomicIntegerFieldUpdater, AtomicReference}
import java.util.function.UnaryOperator

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.{MoreObjects, Objects, Preconditions}
import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.{EmptyPicker, ReadyPicker, Ref, RoundRobinWeightedPicker}
import io.grpc.ConnectivityState._
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.util.ForwardingSubchannel
import io.grpc.{Attributes, ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}

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

  override def handleResolvedAddresses(resolvedAddresses: LoadBalancer.ResolvedAddresses): Unit = {
    val servers: java.util.List[EquivalentAddressGroup] = resolvedAddresses.getAddresses
    val currentAddrs: util.Set[EquivalentAddressGroup] = subChannels.keySet
    val latestAddrs: util.Map[EquivalentAddressGroup, EquivalentAddressGroup] = stripAttrs(servers)
    val removedAddrs: util.Set[EquivalentAddressGroup] = setsDifference(currentAddrs, latestAddrs.keySet)
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
            subchannel.start((state: ConnectivityStateInfo) => {
              processSubchannelState(subchannel, state)
            })
            subChannels.put(strippedAddressGroup, subchannel)
            subchannel.requestConnection()
        }
      })
    val removedSubchannels: util.ArrayList[Subchannel] = new util.ArrayList[Subchannel]()
    removedAddrs.forEach(addressGroup => removedSubchannels.add(subChannels.remove(addressGroup))
    )
    updateBalancingState()
    removedSubchannels.forEach(removedSubchannel => shutdownSubchannel(removedSubchannel))
  }

  override def handleNameResolutionError(error: Status): Unit = {
    eitherPicker.get() match {
      case Left(readyPicker) => helper.updateBalancingState(TRANSIENT_FAILURE, readyPicker)
      case Right(_) => helper.updateBalancingState(TRANSIENT_FAILURE, EmptyPicker(error))
    }
  }

  private def processSubchannelState(subchannel: Subchannel, connectivityStateInfo: ConnectivityStateInfo): Unit = {
    // TODO: verify this returns Unit at this point
    if (subChannels.get(stripAttrs(subchannel.getAddresses)) ne subchannel) {
      Unit
    }
    if (connectivityStateInfo.getState eq IDLE) {
      subchannel.requestConnection()
    }
    val subchannelStateRef: RoundRobinWeightedLoadBalancer.Ref[ConnectivityStateInfo] = getSubchannelStateInfoRef(subchannel)
    if (subchannelStateRef.value.get.getState == TRANSIENT_FAILURE) {
      if (connectivityStateInfo.getState == CONNECTING || connectivityStateInfo.getState == IDLE) {
        Unit
      }
    }
    subchannelStateRef.value.updateAndGet((t: ConnectivityStateInfo) => t)
    updateBalancingState()
  }

  private def shutdownSubchannel(subchannel: LoadBalancer.Subchannel): Unit = {
    subchannel.shutdown()
    getSubchannelStateInfoRef(subchannel).value.getAndSet(ConnectivityStateInfo.forNonError(SHUTDOWN))
  }

  override def shutdown(): Unit = {
    getSubchannels.forEach(subchannel => shutdownSubchannel(subchannel))
  }

  private val EMPTY_OK = Status.OK.withDescription("no subchannels ready")

  private def updateBalancingState(): Unit = {
    val activeList: util.List[LoadBalancer.Subchannel] = filterNonFailingSubchannels(getSubchannels)
    if (activeList.isEmpty) { // No READY subchannels, determine aggregate state and error status
      val isConnecting: AtomicBoolean = new AtomicBoolean(false)
      val aggStatus: AtomicReference[Status] = new AtomicReference[Status](EMPTY_OK)
      subChannels
        .values
        .forEach(subchannel => {
          val stateInfo: ConnectivityStateInfo = getSubchannelStateInfoRef(subchannel).value.get()
          // This subchannel IDLE is not because of channel IDLE_TIMEOUT,
          // in which case LB is already shutdown.
          // RRLB will request connection immediately on subchannel IDLE.
          if ((stateInfo.getState eq CONNECTING) || (stateInfo.getState eq IDLE)) isConnecting.getAndSet(true)
          if ((aggStatus.get() eq EMPTY_OK) || !aggStatus.get.isOk) aggStatus.updateAndGet((t: Status) => t)
        })
      // Set the connectivity state based on whether subchannel is connecting or not.
      val state: ConnectivityState = if (isConnecting.get) CONNECTING else TRANSIENT_FAILURE
      updateBalancingState(
        state,
        // If all subchannels are TRANSIENT_FAILURE, return the Status associated with
        // an arbitrary subchannel, otherwise return OK.
        EmptyPicker(aggStatus.get())
      )
    } else { // initialize the Picker to a random start index to ensure that a high frequency of Picker
      // churn does not skew subchannel selection.
      val startIndex: Int = random.nextInt(activeList.size)
      updateBalancingState(READY, ReadyPicker(activeList, startIndex))
    }
  }

  private def updateBalancingState(state: ConnectivityState, picker: RoundRobinWeightedPicker): Unit = {
    val equivalentPicker = eitherPicker.get() match {
      case Left(readyPicker) => readyPicker.isEquivalentTo(picker)
      case Right(emptyPicker) => emptyPicker.isEquivalentTo(picker)
    }
    if ((state ne currentState.get()) || !equivalentPicker) {
      helper.updateBalancingState(state, picker)
      currentState.getAndUpdate((t: ConnectivityState) => t)
      eitherPicker.get() match {
        case Left(_) => eitherPicker.getAndUpdate(
          (t: Either[ReadyPicker, EmptyPicker]) => t)
        case Right(_) =>  eitherPicker.getAndUpdate(
          (t: Either[ReadyPicker, EmptyPicker]) => t)
      }
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

  /**
   * Converts list of EquivalentAddressGroup to EquivalentAddressGroup set and
   * remove all attributes. The values are the original EAGs.
   */
  private def stripAttrs(groupList: util.List[EquivalentAddressGroup]): util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup] = {
    val addrs = new util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup](groupList.size * 2)
    groupList.forEach(group => addrs.put(stripAttrs(group), group))
    addrs
  }

  private def stripAttrs(eag: EquivalentAddressGroup): EquivalentAddressGroup = new EquivalentAddressGroup(eag.getAddresses)

  private[wookiee] def getSubchannels: util.Collection[Subchannel] = subChannels.values

  // Gets subchannel's state info reference, if reference is null a null pointer exception is thrown.
  // Otherwise, the reference is returned to the calling method.
  private def getSubchannelStateInfoRef(subchannel: LoadBalancer.Subchannel): Ref[ConnectivityStateInfo] = {
    val maybeStateVal: Option[Ref[ConnectivityStateInfo]] = Option(subchannel.getAttributes.get(STATE_INFO))
    maybeStateVal.get match {
      case stateVal => stateVal
      case _ =>
        throw new NullPointerException("STATE_INFO") // TODO: is there something else to do here instead of throwing exception?
    }
  }

  // package-private to avoid synthetic access
  private[wookiee] def isReady(subchannel: Subchannel) = {
    val maybeStateVal: Ref[ConnectivityStateInfo] = getSubchannelStateInfoRef(subchannel)
    maybeStateVal.value.get.getState == READY
  }

  private def setsDifference[T](a: util.Set[T], b: util.Set[T]) = {
    val aCopy = new util.HashSet[T](a)
    aCopy.removeAll(b)
    aCopy
  }

  /*private def connectionRequest(
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
  }*/

}

object RoundRobinWeightedLoadBalancer {
  sealed trait ConnectionState
  case object IDLE extends ConnectionState
  case object CONNECTING extends ConnectionState
  case object READY extends ConnectionState
  case object TRANSIENT_FAILURE extends ConnectionState
  val maybeList: AtomicReference[Option[List[Subchannel]]] = new AtomicReference[Option[List[Subchannel]]]()

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
  abstract class RoundRobinWeightedPicker() extends SubchannelPicker {
    def isEquivalentTo (picker: RoundRobinWeightedLoadBalancer.RoundRobinWeightedPicker): Boolean
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

    override private[wookiee] def isEquivalentTo(picker: RoundRobinWeightedPicker): Boolean = {
      val other = picker.asInstanceOf[ReadyPicker]
      // the lists cannot contain duplicate subchannels
      (other eq this) || (list.size == other.list.size && new util.HashSet[LoadBalancer.Subchannel](list).containsAll(other.list))
    }
  }

  case class EmptyPicker(status: Status) extends RoundRobinWeightedPicker {

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult = {
      if (status.isOk) PickResult.withNoResult
      else PickResult.withError(status)
    }

    override private[wookiee] def isEquivalentTo(picker: RoundRobinWeightedPicker) =
      Objects.equal(status, picker.asInstanceOf[EmptyPicker].status) || (status.isOk && picker.asInstanceOf[EmptyPicker].status.isOk)

    override def toString: String = MoreObjects.toStringHelper(classOf[RoundRobinWeightedLoadBalancer.EmptyPicker]).add("status", status).toString
  }

  class Ref[A](newVal: A) {
    val value: AtomicReference[A] = new AtomicReference[A](newVal)
  }
}
