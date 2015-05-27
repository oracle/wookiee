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

import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigValueType}
import com.webtrends.harness.component.kafka.actor.KafkaConsumerProxy.BrokerSpec
import com.webtrends.harness.component.kafka.health.KafkaProducerHealthCheck
import com.webtrends.harness.component.kafka.util.KafkaSettings
import com.webtrends.harness.component.zookeeper.ZookeeperAdapter
import com.webtrends.harness.health.ComponentState
import com.webtrends.harness.logging.ActorLoggingAdapter
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import net.liftweb.json.{NoTypeHints, Serialization}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object KafkaProducer {
  case class EventToWrite(topic: String, event: Array[Byte])

  case class EventsToWrite(topic: String, events: List[Array[Byte]])

  case class EventsToWriteWithAck(topic: String, ackId: String, events: Array[Byte]*)

  case class EventWriteAck(ackId: Either[String, Throwable])

  case class WriteHealthState(name: String, healthy: Boolean, status: String)

  def props(): Props = Props[KafkaProducer]
}

class KafkaProducer extends Actor
  with ActorLoggingAdapter with  KafkaProducerHealthCheck with ZookeeperAdapter with KafkaSettings {
  import KafkaProducer._
  implicit val timeout = Timeout(10000, TimeUnit.MILLISECONDS)

  var dataProducer: Option[Producer[String, Array[Byte]]] = None
  val formats = Serialization.formats(NoTypeHints)

  var partitionKey = 0L

  val evenPartitionDistribution = Try { kafkaConfig.getBoolean("even-partition-distribution")} getOrElse true

  override def preStart() = {
    resetProducer()
    super.preStart()
  }

  override def postStop() = {
    log.info("Stopping Kafka Writer")
    super.postStop()
    if (dataProducer.isDefined) {
      dataProducer.get.close()
      dataProducer = None
    }
  }


  def receive:Receive = healthReceive orElse {
    case EventToWrite(topic, event) => sendData(topic, Seq(event))

    case EventsToWrite(topic, events) => sendData(topic, events)

    case msg: EventsToWriteWithAck =>
      sender() ! EventWriteAck(sendData(msg.topic, msg.events, msg.ackId))
  }


  /**
   * 
   * @param topic
   * @param ackId
   * @param eventMessages
   * @return
   */
  def sendData(topic: String, eventMessages: Seq[Array[Byte]], ackId: String = ""): Either[String, Throwable] ={
    Try {
      ensureProducer()
      
      val keyedMessages = eventMessages.withFilter(_.length > 0)
                                       .map(keyedMessage(topic, _))

      //Only Send if we have 1 valid messages
      if(keyedMessages.nonEmpty) {
        dataProducer.foreach(_.send(keyedMessages: _*))
      }

      setHealth(ComponentState.NORMAL, "Messages sending successfully")
      Left(ackId)
    } recover {
      case ex:Exception =>
        log.error("Unable To write Event", ex)
        setHealth(ComponentState.CRITICAL, "Last message failed to send")
        resetProducer()
        Right(ex)
    } get
  }

  private def keyedMessage(topic: String, value: Array[Byte]): KeyedMessage[String, Array[Byte]] = {
    val keyedMessage =
      if(evenPartitionDistribution)
        new KeyedMessage[String, Array[Byte]](topic, partitionKey.toString, value)
      else
        new KeyedMessage[String, Array[Byte]](topic, value)

    partitionKey += 1

    keyedMessage
  }

  /**
   *
   */
  def ensureProducer() = {
    if (dataProducer.isEmpty) {
      resetProducer()
    }
  }



  def resetProducer() = {
    if (dataProducer.isDefined) {
      dataProducer.get.close()
      dataProducer = None
    }
    dataProducer = Some(new Producer(new ProducerConfig(configToProps(context.system.settings.config.getConfig("wookie-kafka.producer")))))
    log.info("Kafka Producer Started")
  }

  def getKafkaNodes(config: Config): String = {
    if (config.hasPath("metadata.broker.list")) {
      return config.getString("metadata.broker.list")
    }
    if (!config.hasPath("broker.root")) {
      throw new ExceptionInInitializerError("Must either set metadata.broker.list or broker.root in producer config")
    }

    val brokerRoot = config.getString("broker.root")
    val brokerPath = brokerRoot + "/brokers/ids"
    val rawBrokerFuture = getChildren(brokerPath)
    var brokers = ""
    val rawBrokerData = Await.result(rawBrokerFuture, 10 seconds)
    rawBrokerData foreach { data =>
      val dataPath = brokerRoot + "/brokers/ids/" + data._1
      val dataFut = Await.result(getData(dataPath), 10 seconds)

      val brokerData = Serialization.read(new String(dataFut, utf8))(formats, manifest[BrokerSpec])
      if (!brokers.isEmpty) {
        brokers = brokers + ","
      }
      brokers = brokers + brokerData.host + ":" + brokerData.port
    }

    if (brokers.isEmpty) {
      throw new ExceptionInInitializerError("No brokers found in ZK! Can't write to anywhere. Broker root: " + brokerRoot)
    }
    brokers
  }

  def configToProps(config: Config): Properties = {
    val props: Properties = new Properties
    import scala.collection.JavaConversions._
    config.entrySet.foreach { entry =>
      entry.getValue.valueType match {
        case ConfigValueType.STRING =>
          props.put(entry.getKey, config.getString(entry.getKey))
        case ConfigValueType.NUMBER =>
          props.put(entry.getKey, config.getNumber(entry.getKey).toString)
        case _ =>
        // Ignore other types
      }
    }
    val brokerList = getKafkaNodes(context.system.settings.config.getConfig("wookie-kafka.producer"))
    props.put("key.serializer.class","kafka.serializer.StringEncoder")
    props.put("metadata.broker.list", brokerList)
    props
  }

  class ZkBrokerInfo {
    var host: String = ""
    var jmx_port: Int = 0
    var port: Int = 0
    var version: Int = 0
    var timestamp: Long = 0L

    def getHost: String = {
      host
    }

    def getPort: Int = {
      port
    }
  }
}
