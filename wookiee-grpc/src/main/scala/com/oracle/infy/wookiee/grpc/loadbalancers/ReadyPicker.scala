package com.oracle.infy.wookiee.grpc.loadbalancers

import com.google.common.base.MoreObjects
import com.oracle.infy.wookiee.grpc.impl.WookieeNameResolver
import io.grpc.LoadBalancer.{PickResult, Subchannel, SubchannelPicker}
import io.grpc.{Attributes, LoadBalancer, Status}

case class EmptyPicker(status: Status) extends SubchannelPicker {

  override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): LoadBalancer.PickResult =
    if (status.isOk) PickResult.withNoResult
    else PickResult.withError(status)

  override def toString: String =
    MoreObjects.toStringHelper(classOf[EmptyPicker]).add("status", status).toString
}

// There are two cases for a subchannel: either it is in a READY state, or it is in some other state. If it isn't READY, depending
// on its state the STATE_INFO will need to be updated. If it is READY, then the ready picker will pick from a list of subchannels
// based on which has the smallest load. If the subchannel is not in a READY state, then the empty picker will determine whether
// the subchannel is in a non-error state, and if so it will continue normally, otherwise it will throw an exception.
sealed abstract class ReadyPicker(subchannels: List[Subchannel]) extends SubchannelPicker {
  def list: List[Subchannel] = subchannels
}

case class WeightedReadyPicker(subchannels: List[Subchannel]) extends ReadyPicker(subchannels) {

  override def pickSubchannel(args: LoadBalancer.PickSubchannelArgs): PickResult =
    nextSubchannel match {
      case Some(subchannel) =>
        PickResult.withSubchannel(subchannel)

      case None => PickResult.withError(Status.UNKNOWN)
    }

  override def toString: String =
    MoreObjects.toStringHelper(classOf[ReadyPicker]).add("list", list).toString

  private def nextSubchannel: Option[Subchannel] = {
    val validList =
      list.filter(p => !p.getAttributes.get(WookieeNameResolver.METADATA).quarantined)
    val sortedList = validList.sortBy(
      subchannel => sortByLoad(subchannel.getAttributes)
    )
    sortedList.headOption
  }

  def sortByLoad(attrs: Attributes): Int =
    attrs
      .get(WookieeNameResolver.METADATA)
      .load

  private[wookiee] def getList: List[Subchannel] = list
}
