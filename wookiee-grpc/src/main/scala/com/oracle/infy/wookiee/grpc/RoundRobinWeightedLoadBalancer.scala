package com.oracle.infy.wookiee.grpc
import java.util
import java.util.List
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.function.UnaryOperator

import com.google.common.base.{MoreObjects, Objects}
import com.oracle.infy.wookiee.grpc.RoundRobinWeightedLoadBalancer.{
  EmptyPicker,
  ReadyPicker,
  Ref,
  RoundRobinWeightedPicker
}
import io.grpc.ConnectivityState._
import io.grpc.LoadBalancer.{CreateSubchannelArgs, PickResult, Subchannel, SubchannelPicker}
import io.grpc.util.ForwardingSubchannel
import io.grpc.{Attributes, ConnectivityState, ConnectivityStateInfo, EquivalentAddressGroup, LoadBalancer, Status}

import scala.util.Random

class RoundRobinWeightedLoadBalancer(helper: LoadBalancer.Helper) extends LoadBalancer {

  private[wookiee] val STATE_INFO: Attributes.Key[Ref[ConnectivityStateInfo]] = Attributes.Key.create("state-info")

  val subChannels: util.HashMap[EquivalentAddressGroup, Subchannel] =
    new util.HashMap[EquivalentAddressGroup, Subchannel]()

  private val EMPTY_OK = Status.OK.withDescription("no subchannels ready")

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
        existingSubchannel match {
          case Some(subchannel: ForwardingSubchannel) =>
            subchannel.updateAddresses(originalAddressGroup.asInstanceOf[java.util.List[EquivalentAddressGroup]])
          case _ =>
            val subchannelAttrs = Attributes
              .newBuilder
              .set(
                STATE_INFO,
                new RoundRobinWeightedLoadBalancer.Ref[ConnectivityStateInfo](ConnectivityStateInfo.forNonError(IDLE))
              )
// TODO: change to option to handle null case
//            val subchannel = checkNotNull[Subchannel](
//              helper.createSubchannel(
//                CreateSubchannelArgs
//                  .newBuilder
//                  .setAddresses(originalAddressGroup)
//                  .setAttributes(subchannelAttrs.build)
//                  .build
//              ),
//              "subchannel"
//            )
            val subchannel = helper.createSubchannel(
              CreateSubchannelArgs
                .newBuilder()
                .setAddresses(originalAddressGroup)
                .setAttributes(subchannelAttrs.build())
                .build()
            )
            subchannel.start((state: ConnectivityStateInfo) => {
              processSubchannelState(subchannel, state)
            })
            subChannels.put(strippedAddressGroup, subchannel)
            subchannel.requestConnection()
        }
      })
    val removedSubchannels: util.ArrayList[Subchannel] = new util.ArrayList[Subchannel]()
    removedAddrs.forEach(addressGroup => {
      removedSubchannels.add(subChannels.remove(addressGroup))
      ()
    })
    updateBalancingState()
    removedSubchannels.forEach((removedSubchannel: Subchannel) => shutdownSubchannel(removedSubchannel))
    //shutdown()
  }

  override def handleNameResolutionError(error: Status): Unit = {
    eitherPicker.get() match {
      case Left(readyPicker) => helper.updateBalancingState(TRANSIENT_FAILURE, readyPicker)
      case Right(_)          => helper.updateBalancingState(TRANSIENT_FAILURE, EmptyPicker(error))
    }
  }

  private def processSubchannelState(subchannel: Subchannel, connectivityStateInfo: ConnectivityStateInfo): Unit = {
    if (subChannels.get(stripAttrs(subchannel.getAddresses)) == subchannel) {
      if (connectivityStateInfo.getState == IDLE) {
        subchannel.requestConnection()
      }
      val subchannelStateRef: RoundRobinWeightedLoadBalancer.Ref[ConnectivityStateInfo] = getSubchannelStateInfoRef(
        subchannel
      )
      if (!(subchannelStateRef.value.get.getState.equals(TRANSIENT_FAILURE) && (connectivityStateInfo
            .getState
            .equals(CONNECTING) || connectivityStateInfo.getState.equals(IDLE)))) {
        subchannelStateRef
          .value
          .getAndUpdate(new UnaryOperator[ConnectivityStateInfo] {
            override def apply(t: ConnectivityStateInfo): ConnectivityStateInfo = connectivityStateInfo
          })
        updateBalancingState()
      }
    }
    ()
  }

  private def shutdownSubchannel(subchannel: Subchannel): Unit = {
    subchannel.shutdown()
    getSubchannelStateInfoRef(subchannel)
      .value
      .getAndUpdate(new UnaryOperator[ConnectivityStateInfo] {
        override def apply(t: ConnectivityStateInfo): ConnectivityStateInfo =
          ConnectivityStateInfo.forNonError(SHUTDOWN)
      })
    ()
  }

  override def shutdown(): Unit = {
    getSubchannels.forEach(subchannel => shutdownSubchannel(subchannel))
  }

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
          ()
        })
      // Set the connectivity state based on whether subchannel is connecting or not.
      val state: ConnectivityState = if (isConnecting.get) CONNECTING else TRANSIENT_FAILURE
      updateBalancingState(
        state,
        // If all subchannels are TRANSIENT_FAILURE, return the Status associated with
        // an arbitrary subchannel, otherwise return OK.
        Right(EmptyPicker(aggStatus.get()))
      )
    } else { // initialize the Picker to a random start index to ensure that a high frequency of Picker
      // churn does not skew subchannel selection.
      val startIndex: Int = random.nextInt(activeList.size)
      updateBalancingState(READY, Left(ReadyPicker(activeList, startIndex)))
    }
  }

  private def updateBalancingState(state: ConnectivityState, picker: Either[ReadyPicker, EmptyPicker]): Unit = {
    val notEquivalentState: Boolean = state ne currentState.get()
    val equivalentPicker: Boolean = RoundRobinWeightedPicker.isEquivalentTo(eitherPicker.get, picker)
    if (notEquivalentState | !equivalentPicker) {
      currentState.getAndUpdate((t: ConnectivityState) => t)
      picker match {
        case Left(readyPicker) =>
          helper.updateBalancingState(state, readyPicker)
          eitherPicker.getAndUpdate(new UnaryOperator[Either[ReadyPicker, EmptyPicker]] {
            override def apply(t: Either[ReadyPicker, EmptyPicker]): Either[ReadyPicker, EmptyPicker] =
              Left(readyPicker)
          })
        case Right(emptyPicker) =>
          helper.updateBalancingState(state, emptyPicker)
          eitherPicker.getAndUpdate(new UnaryOperator[Either[ReadyPicker, EmptyPicker]] {
            override def apply(t: Either[ReadyPicker, EmptyPicker]): Either[ReadyPicker, EmptyPicker] =
              Right(emptyPicker)
          })
      }
    }
    ()
  }

  /**
    * Filters out non-ready subchannels.
    */
  private def filterNonFailingSubchannels(subchannels: util.Collection[LoadBalancer.Subchannel]) = {
    val readySubchannels = new util.ArrayList[LoadBalancer.Subchannel](subchannels.size)
    subchannels.forEach(subchannel => {
      if (isReady(subchannel)) {
        readySubchannels.add(subchannel)
      }
      ()
    })
    readySubchannels
  }

  /**
    * Converts list of EquivalentAddressGroup to EquivalentAddressGroup set and
    * remove all attributes. The values are the original EAGs.
    */
  private def stripAttrs(
      groupList: util.List[EquivalentAddressGroup]
  ): util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup] = {
    val addrs = new util.HashMap[EquivalentAddressGroup, EquivalentAddressGroup](groupList.size * 2)
    groupList.forEach { group =>
      addrs.put(stripAttrs(group), group)
      ()
    }
    addrs
  }

  private def stripAttrs(eag: EquivalentAddressGroup): EquivalentAddressGroup =
    new EquivalentAddressGroup(eag.getAddresses)

  private[wookiee] def getSubchannels: util.Collection[Subchannel] = subChannels.values

  // Gets subchannel's state info reference, if reference is null a null pointer exception is thrown.
  // Otherwise, the reference is returned to the calling method.
  private def getSubchannelStateInfoRef(subchannel: LoadBalancer.Subchannel): Ref[ConnectivityStateInfo] = {
    val maybeStateVal: Option[Ref[ConnectivityStateInfo]] = Option(subchannel.getAttributes.get(STATE_INFO))
    maybeStateVal match {
      case Some(stateVal) => stateVal
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
      //checkNotNull(status, "STATUS")
      EmptyPicker(status)
    }

    def isEquivalentTo(
        currentPicker: Either[ReadyPicker, EmptyPicker],
        picker: Either[ReadyPicker, EmptyPicker]
    ): Boolean = {
      currentPicker match {
        case Left(currentReadyPicker) =>
          picker match {
            case Left(readyPicker) =>
              (currentReadyPicker eq readyPicker) || (currentReadyPicker
                .list
                .size == readyPicker.list.size && new util.HashSet[LoadBalancer.Subchannel](currentReadyPicker.list)
                .containsAll(readyPicker.list))
            case Right(_) => false
          }
        case Right(currentEmptyPicker) =>
          picker match {
            case Left(_) => false
            case Right(emptyPicker) =>
              Objects.equal(currentEmptyPicker.status, emptyPicker.status) || (currentEmptyPicker
                .status
                .isOk && emptyPicker
                .status
                .isOk)
          }
      }
    }
  }

  //TODO: this is where picking (weighted round robin) logic should happen (possibly ?)

  case class ReadyPicker(list: util.List[Subchannel], startIndex: Int) extends SubchannelPicker {

//    private val indexUpdater: AtomicIntegerFieldUpdater[ReadyPicker] =
//      AtomicIntegerFieldUpdater.newUpdater(classOf[RoundRobinWeightedLoadBalancer.ReadyPicker], "index")
//
//    @volatile private var index = startIndex - 1
    private val index: AtomicReference[Int] = new AtomicReference[Int](startIndex - 1)

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult =
      PickResult.withSubchannel(nextSubchannel)

    override def toString: String =
      MoreObjects.toStringHelper(classOf[RoundRobinWeightedLoadBalancer.ReadyPicker]).add("list", list).toString

    private def nextSubchannel = {
      val size = list.size()
      //val currentIndex = indexUpdater.incrementAndGet(this)
      val currentIndex = index.updateAndGet(new UnaryOperator[Int] {
        override def apply(t: Int): Int = index.get() + 1
      })
      if (currentIndex >= size) {
        val newIndex = currentIndex % size
        //indexUpdater.compareAndSet(this, currentIndex, newIndex)
        list.get(newIndex)
      } else {
        list.get(currentIndex)
      }
    }

    private[wookiee] def getList: util.List[Subchannel] = list
  }

  case class EmptyPicker(status: Status) extends SubchannelPicker {

    override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult = {
      if (status.isOk) PickResult.withNoResult
      else PickResult.withError(status)
    }

    override def toString: String =
      MoreObjects.toStringHelper(classOf[RoundRobinWeightedLoadBalancer.EmptyPicker]).add("status", status).toString
  }

  class Ref[A](newVal: A) {
    val value: AtomicReference[A] = new AtomicReference[A](newVal)
  }
}
