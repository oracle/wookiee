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

import akka.actor.{Actor, Props}
import com.webtrends.harness.component.kafka.health.KafkaWriterHealthCheck
import com.webtrends.harness.component.kafka.util.{KafkaUtil, KafkaSettings}
import com.webtrends.harness.component.metrics.metrictype.Meter
import com.webtrends.harness.health.ComponentState
import com.webtrends.harness.logging.ActorLoggingAdapter
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import scala.util.Try

object KafkaWriter {
  case class EventToWrite(topic: String, event: Array[Byte])

  case class EventsToWrite(topic: String, events: List[Array[Byte]])

  case class EventsToWriteWithAck(topic: String, ackId: String, events: Array[Byte]*)

  case class EventWriteAck(ackId: Either[String, Throwable])

  def props(): Props = Props[KafkaWriter]
}

class KafkaWriter extends Actor
  with ActorLoggingAdapter with KafkaWriterHealthCheck with KafkaSettings {
  import KafkaWriter._

  lazy val totalEvents = Meter("total-events-per-second")
  lazy val totalBytesPerSecond = Meter("total-bytes-per-second")

  val dataProducer = new KafkaProducer[String, Array[Byte]](KafkaUtil.configToProps(kafkaConfig.getConfig("producer")))
  var partitionKey = 0L
  val evenPartitionDistribution = Try { kafkaConfig.getBoolean("even-partition-distribution") } getOrElse true

  override def postStop() = {
    log.info("Stopping Kafka Writer")
    super.postStop()
    dataProducer.close()
  }

  def receive:Receive = healthReceive orElse {
    case EventToWrite(topic, event) => sendData(topic, Seq(event))

    case EventsToWrite(topic, events) => sendData(topic, events)

    case msg: EventsToWriteWithAck =>
      sender() ! EventWriteAck(sendData(msg.topic, msg.events, msg.ackId))
  }

  def sendData(topic: String, eventMessages: Seq[Array[Byte]], ackId: String = ""): Either[String, Throwable] ={
    Try {
      var bytes = 0L
      val keyedMessages = eventMessages.withFilter(_.length > 0).map { it =>
        bytes += it.length
        val message = keyedMessage(topic, it)

        // If the Kafka target is down completely, the Kafka client provides no feedback.
        // The callback is never called and a get on the returned java Future will block indefinitely.
        // Handling this by waiting on the
        val productionFuture = dataProducer.send(message)
        monitorSendHealth(productionFuture)

        message
      }

      if (keyedMessages.nonEmpty) {
        totalEvents.mark(keyedMessages.size)
        totalBytesPerSecond.mark(bytes)
      }

      if (currentHealth.isEmpty || currentHealth.get.state == ComponentState.NORMAL) {
        Left(ackId)
      }
      else {
        Right(new IllegalStateException("Producer is not healthy"))
      }
    } recover {
      case ex:Exception =>
        log.error("Unable To write Event", ex)
        setHealth(ComponentState.CRITICAL, "Last message failed to send")
        Right(ex)
    } get
  }

  private def keyedMessage(topic: String, value: Array[Byte]): ProducerRecord[String, Array[Byte]] = {
    val keyedMessage =
      if(evenPartitionDistribution)
        new ProducerRecord[String, Array[Byte]](topic, partitionKey.toString, value)
      else
        new ProducerRecord[String, Array[Byte]](topic, value)

    partitionKey += 1
    keyedMessage
  }
}
