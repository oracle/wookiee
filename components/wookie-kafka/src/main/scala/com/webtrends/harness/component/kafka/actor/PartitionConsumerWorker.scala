/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.kafka.actor


import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.component.kafka.actor.KafkaConsumerProxy.{FetchConsumer, KafkaRefreshReq}
import com.webtrends.harness.component.kafka.actor.OffsetManager.{GetOffsetData, OffsetData, OffsetDataResponse, StoreOffsetData}
import com.webtrends.harness.component.kafka.actor.PartitionConsumerWorker._
import com.webtrends.harness.component.kafka.health.KafkaHealthState
import com.webtrends.harness.component.kafka.receive.MessageResponse
import com.webtrends.harness.component.kafka.util._
import com.webtrends.harness.component.zookeeper.{Curator, ZookeeperAdapter}
import kafka.api.FetchRequestBuilder
import kafka.common.{ErrorMapping, InvalidMessageSizeException, LeaderNotAvailableException, OffsetOutOfRangeException}
import kafka.consumer.SimpleConsumer
import org.apache.curator.framework.recipes.locks.{InterProcessSemaphoreV2, Lease}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


/**
 * Fetch Mode - can be 'automatic' or 'manual'
 * Automatic - Automatically ask Kafka for more messages
 * Manual - The client decides when to ask for more by invoking fetchRequest(someOffset)
 **/
object ConsumptionFetchMode extends Enumeration {
  type ConsumptionFetchMode = Value
  val AUTOMATIC, MANUAL = Value
}

object PartitionConsumerWorker {
  case object Stop
  case object Start
  // Worker is reporting that it has stopped
  case class WorkerStopped(name: String)

  case object CommitOffset

  case class FetchErrorBadOffset(nextAvailableOffset: Long)
  case class FetchErrorPartitionNotAvailable(offsetRequested: Long)
  case class FetchRes(messages: KafkaMessageSet, nextOffset: Long)

  // FSM States
  trait State
  case object Stopped extends State
  case object Starting extends State
  case object Consuming extends State

  trait Lock
  case class Acquired(lease: Lease) extends Lock
  case object Unlocked extends Lock
}

class PartitionConsumerWorker(kafkaProxy: ActorRef, assign: PartitionAssignment, offsetManager: ActorRef)
  extends LoggingFSM[State, Lock] with Actor with ZookeeperAdapter with KafkaSettings {
  implicit val timeout = Timeout(2000, TimeUnit.MILLISECONDS)
  import ConsumptionFetchMode._
  import context._

  val decoder = utf8.newDecoder

  val topic = assign.topic
  val partition = assign.partition
  val host = assign.host
  val cluster = assign.cluster
  val name = assign.assignmentName
  val consumerWait = Try { kafkaConfig.getLong("consumer-wait-millis") } getOrElse 5000L
  val lockPath = s"$appRootPath/locks/${cluster}_${topic}_$partition"

  // Harness 3.0 does not provide distributed lock support. Need to pull the curator instance and create our own
  val curator = Curator(zkConf.get).client

  val zkCommitRate = kafkaConfig.getLong("zk-offset-commit-rate-millis")

  // Add scheduled jobs to this list, they will be cancelled on Stop
  var scheduler: List[Cancellable] = List.empty

  // The acknowledged offset
  protected var ackedOffset = 0L

  // OVERRIDE to set custom offset writing
  def formatAckedOffset() = ackedOffset.toString

  // Last offset that was sent over to kafka proxy
  protected var lastSentToProxyOffset = 0L

  // Last offset stored
  protected var lastSentToStorage = 0L

  // Each partition worker needs to lock the partition it is working on to prevent data duplication.
  // We need to ensure that the initial node completes before the new node reads the offsets out of ZK
  protected val lock = new InterProcessSemaphoreV2(curator, lockPath, 1)

  protected var consumer: Option[KafkaConsumer] = None

  /**
   * Fetch Mode - Defaults to automatic
   * @return
   **/
  def fetchMode():ConsumptionFetchMode = AUTOMATIC

  /**
   * Override method to set functionality when consuming, to manage offsets be sure to
   * set ackedOffset when account has executed its desired data processing successfully.
   * @param messageResponse message received by worker on its assigned partition
   */
  def onReceive(messageResponse: MessageResponse) = {
    ackedOffset = messageResponse.nextOffsetOfSet
  }


  // A worker can be in one of 4 standard states:
  // 1. Stopped  -  The worker has been created and told to process data, but it is not yet ready to read from kafka.
  //                If told to start, the worker will obtain the distributed lock for this partition,
  //                and read its initial state out of ZK
  // 2. Starting -  The worker has been told to start and has acquired a lock, it will not try to read its initial
  //                state from ZK and start consuming if successful
  // 3. Consuming - The worker has fully started up and is reading data from kafka
  startWith(Stopped, Unlocked)

  // These messages should always be responded to in all states
  whenUnhandled {
    case Event(Stop, aq: Acquired) =>
      goto(Stopped) using Unlocked

    case Event(Stop, Unlocked) =>
      stay()

    case Event(Start, aq: Acquired) =>
      stay()
  }

  onTransition {
    case (Consuming | Starting) -> Stopped =>
      log.info(s"$name: Transitioning state to Stopped")
      stopWorker()
      context.parent ! WorkerStopped(name)

    case Stopped -> Starting =>
      log.debug(s"$name: Transitioning state to Starting")
      offsetManager ! GetOffsetData(partitionName(assign))

    case Starting -> Consuming =>
      log.info(s"$name: Transitioning state to Consuming")
      fetchConsumer()
      if (consumer.isDefined) {
        context.parent ! KafkaHealthState(name, healthy = true, s"Worker started", topic)
        fetchRequest(ackedOffset)
        scheduler = scheduler :+ context.system.scheduler.schedule(zkCommitRate milliseconds,
          zkCommitRate milliseconds, self, CommitOffset)
      }
  }

  protected def fetchConsumer() {
    log.debug(s"$name: Consumer closed, fetching new one")
    consumer = Await.result(kafkaProxy ? FetchConsumer(host), consumerWait milliseconds) match {
      case Some(con) => Some(con.asInstanceOf[KafkaConsumer])
      case None =>
        log.error(s"No consumer found for $host")
        context.parent ! KafkaHealthState(name, healthy = false, s"Could not get consumer", topic)
        self ! Stop
        None
    }
  }

  when(Stopped) {
    case Event(Start, Unlocked) =>
      acquireLock() match {
        case Some(aq) => goto(Starting) using aq
        case None => stay()
      }

    case Event(msg: OffsetDataResponse, aq: Acquired) =>
      stay()
  }

  when(Starting, stateTimeout = 5 seconds) {
    case Event(msg: OffsetDataResponse, aq: Acquired) =>
      msg.data match {
        case Left(data) =>
          processOffsets(data)
        case Right(ex) =>
          log.error("Unable to load offset state")
          goto(Stopped) using Unlocked
      }

    case Event(StateTimeout, aq: Acquired) =>
      goto(Stopped) using Unlocked
  }

  when(Consuming) {
    case Event(msg: FetchRes, aq: Acquired) =>
      consume(msg)
      stay()

    case Event(msg: FetchErrorBadOffset, aq: Acquired) =>
      log.warning(s"$name: Failed to fetch events from kafka. " +
                  s"Offset out of range. Using offset ${msg.nextAvailableOffset}")
      fetchRequest(msg.nextAvailableOffset)
      stay()

    case Event(msg: FetchErrorPartitionNotAvailable, aq: Acquired) =>
      context.parent ! KafkaHealthState(name, healthy = false, s"Failed to fetch events from kafka. Partition not available", topic)
      log.error(s"$name: Unable to fetch events from kafka. Partition no available")
      goto(Stopped) using Unlocked

    case Event(CommitOffset, aq: Acquired) =>
      storeOffset()
      stay()

    case Event(msg: OffsetDataResponse, aq: Acquired) =>
      msg.data match {
        case Left(data) =>
          lastSentToStorage = extractOffset(data)
          stay()
        case Right(ex) =>
          log.error("Unable to save offset state")
          goto(Stopped) using Unlocked
      }
  }

  initialize()

  // Will only store if the ackedOffset has changed or force is true
  protected def storeOffset(force: Boolean = false): Unit = {
    if (force || (ackedOffset > 0 && lastSentToStorage != ackedOffset)) {
       offsetManager ! StoreOffsetData(partitionName(assign), OffsetData(formatAckedOffset().getBytes(utf8)))
    }
  }

  override def postStop(): Unit = {
    stopWorker()
  }

  // Override to process initial offsets differently
  protected def processOffsets(offsetData: OffsetData): State = {
    val offset = extractOffset(offsetData)

    log.debug(s"$name has initialized with offset $offset")
    ackedOffset = offset
    lastSentToProxyOffset = offset
    lastSentToStorage = offset

    goto(Consuming)
  }

  // Can be overridden to read custom offsets
  protected def extractOffset(offsetData:OffsetData):Long = {
    val data = offsetData.asString()
    if(data.isEmpty) 0L else data.toLong
  }

  def stopWorker() = {
    if(ackedOffset > 0) {
      log.debug(s"Shutdown %s, storage offset=%d, proxy offset=%d, acked offset=%d"
        .format(name, lastSentToStorage, lastSentToProxyOffset, ackedOffset))

      offsetManager ! StoreOffsetData(partitionName(assign), OffsetData(formatAckedOffset().getBytes(utf8)))
    }
    scheduler.foreach(_.cancel())
    scheduler = List()

    stateData match {
      case aq: Acquired =>
        lock.returnLease(aq.lease)
        log.debug(s"$name: Released lock")
      case _ => // No lock
    }
    context.parent ! KafkaHealthState(name, healthy = true, null, null)
  }

  def acquireLock(): Option[Acquired] = {
    try {
      val acquiredLease = lock.acquire()
      log.debug(s"$name: Lock acquired")
      Some(Acquired(acquiredLease))
    } catch {
      case e: Throwable =>
        log.error(s"$name: Unable to obtain lock: ${e.getMessage} ")
        None
    }
  }

  /**
   * Takes the list of messages receive in the last fetch and sends them to
   * the main worker method account(...) which should be overridden with the actual
   * work we want to do.
   * @param fetchRes Contains our next offset and the list of messages we just got
   * @return Returns a request for more messages
   */
  def consume(fetchRes: FetchRes) = {
    lastSentToProxyOffset = fetchRes.nextOffset
    onReceive(MessageResponse(fetchRes.messages, fetchRes.nextOffset))

    // After consumption of messages automatically fetch the next
    if(fetchMode() == AUTOMATIC) {
      fetchRequest (fetchRes.nextOffset)
    }
  }

  /**
   * Fetch data from kafka
   */
  protected def fetchRequest(nextOffset: Long) = {
    //log.debug(s"$name fetching offset $nextOffset")
    try {
      if (consumer.forall(_.closed)) {
        fetchConsumer()
      }
      if (consumer.isDefined) {
        self ! fetchTopicPart(topic, partition, nextOffset, clientId)
      }
    } catch {
      case ex: OffsetOutOfRangeException =>
        self ! FetchErrorBadOffset(KafkaUtil.getDesiredAvailableOffset(consumer.get, topic, partition, nextOffset, clientId))

      case ex: InvalidMessageSizeException =>
        log.error(s"Failed to fetch message for $topic:$partition from $host because message was too big for buffer, you need to increase the buffer size!")
        self ! FetchErrorPartitionNotAvailable(nextOffset)

      // Catch all. Respond with a generic error message that will result in the workers waiting before retrying
      // and kick off a refresh of our kafka info
      case ex: Exception =>
        log.error(s"Failed to fetch from [$host] [$topic] [$partition] [$nextOffset] [${ex.getClass.toString}] [${ex.getMessage}]")
        kafkaProxy ! KafkaRefreshReq(light = true)
        self ! FetchErrorPartitionNotAvailable(nextOffset)
    }
  }

  def fetchTopicPart(topic: String, part: Int, offset: Long, clientId: String): FetchRes = {
    val fetch = new FetchRequestBuilder()
      .clientId(clientId)
      .addFetch(topic, part, offset, 150000)
      .build()

    val fetchResponse = consumer.get.fetch(fetch)
    if (fetchResponse.hasError) {
      ErrorMapping.maybeThrowException(fetchResponse.errorCode(topic, part))
    }

    val msgSet = fetchResponse.messageSet(topic, part)

    val msgSeq = KafkaMessageSet(msgSet, offset) // This is needed since if Kafka is compressing the messages, the fetch request will return an entire compressed block even if the requested offset isn't the beginning of the compressed block. Thus a message we saw previously may be returned again.

    FetchRes(msgSeq, if (msgSet.size > 0) msgSet.last.nextOffset else offset)
  }
}
