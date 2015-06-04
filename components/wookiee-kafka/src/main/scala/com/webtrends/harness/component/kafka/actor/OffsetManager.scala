package com.webtrends.harness.component.kafka.actor

import java.nio.charset.{Charset, StandardCharsets}

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.webtrends.harness.component.kafka.health.ZKHealthState
import com.webtrends.harness.component.zookeeper.ZookeeperAdapter
import com.webtrends.harness.logging.ActorLoggingAdapter
import org.apache.zookeeper.KeeperException.NoNodeException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.collection.mutable

object OffsetManager {
  def props(appRoot: String, timeout: Timeout = 5 seconds): Props =
    Props(new OffsetManager(appRoot, timeout))

  case class OffsetData(data: Array[Byte]) {
    def asString(charset:Charset = StandardCharsets.UTF_8) = new String(data, charset)
  }

  case class GetOffsetData(path: String)
  case class StoreOffsetData(path: String, offsetData: OffsetData)

  case class OffsetDataResponse(data: Either[OffsetData, Throwable])
}

class OffsetManager(appRoot: String, timeout:Timeout) extends Actor
with ActorLoggingAdapter with ZookeeperAdapter {
  import OffsetManager._
  implicit val implicitTimeout = timeout
  val createPaths = new mutable.HashSet[String]
  val offsetPath = s"$appRoot/offsets"

  def receive: Receive = {
    case msg: GetOffsetData => getOffsetState(msg)

    case msg: StoreOffsetData => storeOffsetState(msg)
  }

  /**
   * Get the state from zk and send back offset data response
   */
  def getOffsetState(req: GetOffsetData) = {
    val originalSender = sender()
    val parent = context.parent
    //Path to write to zookeeper
    val path = s"$offsetPath/${req.path}"
    getData(path).onComplete {
      case Success(data) =>
        Try {
          originalSender ! OffsetDataResponse(Left(OffsetData(data)))
          parent ! healthy(path)
        } recover {
          case ex =>
            log.error("Error retrieving state from zk", ex)
            originalSender ! OffsetDataResponse(Right(ex))
            parent ! unhealthy(path)
        }
      case Failure(err) =>
        err match {
          case ex: NoNodeException =>
            originalSender ! OffsetDataResponse(Left(OffsetData(Array.empty[Byte])))
            parent ! healthy(path)
          case ex =>
            log.error("Unable to get state from Zk", ex)
            originalSender ! OffsetDataResponse(Right(ex))
            parent ! unhealthy(path)
        }
    }
  }

  def storeOffsetState(req: StoreOffsetData, create: Boolean = false): Unit = {
    val path = s"$offsetPath/${req.path}"

    // ZNode should be create if new and not be ephemeral
    setDataAsync(path, req.offsetData.data, create = create || !createPaths.contains(path), ephemeral = false)
    sender() ! OffsetDataResponse(Left(OffsetData(req.offsetData.data)))
    context.parent ! healthy(path)
    createPaths.add(path)
  }

  private def unhealthy(path: String) =
    ZKHealthState(path, healthy = false, s"Failed to fetch offset state $path from ZK")

  private def healthy(path: String) =
    ZKHealthState(path, healthy = true, s"Successfully fetched state $path from ZK")
}
