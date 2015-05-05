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

import akka.actor.{Props, Actor}
import com.webtrends.harness.component.kafka.KafkaConsumerCoordinator.TopicPartitionResp
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.component.kafka.actor.PartitionConsumerWorker.{FetchErrorBadOffset, FetchErrorPartitionNotAvailable}
import com.webtrends.harness.component.kafka.util.{KafkaMessageSet, KafkaSettings, KafkaUtil}
import com.webtrends.harness.component.zookeeper.{ZookeeperAdapter, ZookeeperEventAdapter}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.CheckHealth
import kafka.api.{FetchRequestBuilder, TopicMetadataRequest}
import kafka.common._
import kafka.consumer.SimpleConsumer
import net.liftweb.json.{NoTypeHints, Serialization}

import scala.collection.mutable
import scala.concurrent.duration._


object KafkaConsumerProxy {
  /**
   * BrokerSpec maps the JSON used by Kafka 0.8 to describe a broker as
   * written by it in Zookeeper.
   * @param host The broker hostname.
   * @param port The broker port.
   * @param cluster The zookeeper cluster this broker belongs to
   */
  case class BrokerSpec(host: String, port: Int, cluster: String)

  case class KafkaRefreshReq(light: Boolean = false)

  case class FetchReq(topic: String, part: Int, offset: Long, host: String)
  case class FetchRes(messages: KafkaMessageSet, nextOffset: Long)

  case object TopicPartitionReq

  def props() = Props[KafkaConsumerProxy]
}

class KafkaConsumerProxy extends Actor with KafkaSettings
  with ActorLoggingAdapter with ZookeeperAdapter with ZookeeperEventAdapter {

  def configuredTopics = topicMap.keys.toList
  val formats = Serialization.formats(NoTypeHints)
  import KafkaConsumerProxy._
  import context.dispatcher

  val bufferSize = 1024*1024
  val clientId = s"kk_$hostname"

  var brokers: Option[Seq[BrokerSpec]] = None
  val consumersByHost = new mutable.HashMap[String,Option[SimpleConsumer]]()
  val hostsByTopicParts = new mutable.HashMap[(String, Int), Option[List[String]]]()
  val partitionsByTopic = new mutable.HashSet[PartitionAssignment]()
  val clusters = new mutable.HashMap[String, String]()
  var health = HealthComponent("kafka-proxy", ComponentState.NORMAL, "Proxy has not acquired brokers yet")

  override def preStart() = {
    self ! KafkaRefreshReq(false)
  }

  def receive = configReceive orElse {
    // We will first pull the broker data from ZK and use that
    // To make topic meta data requests to get the partition information
    case KafkaRefreshReq(light) =>
      try {
        log.info("Refreshing kafka broker/partition data")
        if (!light) clearKafkaMappings()
        requestBrokerInfo()
        health = HealthComponent("kafka-proxy", ComponentState.NORMAL, "Successfully fetched broker data")
      } catch {
        case e: Exception =>
          log.error("Failed to fetch broker info from ZK. Retrying in 10 seconds", e)
          health = HealthComponent("kafka-proxy", ComponentState.DEGRADED, "Failed to fetch broker info from ZK. Retrying in 10 seconds")
          context.system.scheduler.scheduleOnce(10 seconds) { self ! KafkaRefreshReq(light) }
      }

    case TopicPartitionReq =>
      sender ! TopicPartitionResp(partitionsByTopic.toSet[PartitionAssignment])

    case FetchReq(topic,part,offset,host) =>
      try {
        if (consumersByHost(host).isDefined) {
          sender ! fetchTopicPart(consumersByHost(host).get, topic, part, offset, clientId)
        } else {
          throw new LeaderNotAvailableException()
        }
      } catch {
        case ex: OffsetOutOfRangeException =>
          sender ! FetchErrorBadOffset(KafkaUtil.getDesiredAvailableOffset(consumersByHost(host).get, topic, part, offset, clientId))

        case ex: InvalidMessageSizeException =>
          log.error(s"Failed to fetch message for $topic:$part from $host because message was too big for buffer, you need to increase the buffer size!")
          sender ! FetchErrorPartitionNotAvailable(offset)

        // Catch all. Respond with a generic error message that will result in the workers waiting before retrying
        // and kick off a refresh of our kafka info
        case ex: Exception =>
          log.error(s"Failed to fetch from [$host] [$topic] [$part] [$offset] [${ex.getClass.toString}] [${ex.getMessage}]")
          sender ! FetchErrorPartitionNotAvailable(offset)
          refreshBrokerInfoIfNeeded()
      }

    case CheckHealth => sender() ! health
  }

  override def renewConfiguration() = {
    super.renewConfiguration()
    self ! KafkaRefreshReq(false)
  }

  def fetchTopicPart(consumer: SimpleConsumer, topic: String, part: Int, offset: Long, clientId: String): FetchRes = {
    val fetch = new FetchRequestBuilder()
      .clientId(clientId)
      .addFetch(topic, part, offset, 150000)
      .build()

    val fetchResponse = consumer.fetch(fetch)
    if (fetchResponse.hasError) {
      ErrorMapping.maybeThrowException(fetchResponse.errorCode(topic, part))
    }

    val msgSet = fetchResponse.messageSet(topic, part)

    val msgSeq = KafkaMessageSet(msgSet, offset) // This is needed since if Kafka is compressing the messages, the fetch request will return an entire compressed block even if the requested offset isn't the beginning of the compressed block. Thus a message we saw previously may be returned again.

    FetchRes(msgSeq, if (msgSet.size > 0) msgSet.last.nextOffset else offset)
  }

  def requestBrokerInfo() = {
    log.info("Using kafka-hosts to set consumed hosts, {}", kafkaSources)
    var brokerData = Seq[BrokerSpec]()
    kafkaSources foreach { s =>
      s._2 foreach { broker =>
        val hostPort: Array[String] = broker.split(":")
        val host: String = hostPort(0)
        val port: Int = if (hostPort.length == 2) hostPort(1).toInt else 9092
        brokerData = brokerData :+ BrokerSpec(host, port, s._1)
        clusters.put(host, s._1)
      }
    }

    processBrokerData(brokerData)
  }

  def processBrokerData(newBrokers: Seq[BrokerSpec]) = {
    log.debug("Processing brokers {}", newBrokers.toString())
    brokers = Some(newBrokers)

    brokers.get.filter { it => partitionsByTopic.forall { part => part.host != it.host } }.map { b =>
      try {
        consumersByHost.put(b.host, Some(new SimpleConsumer(b.host, b.port, 15000, bufferSize, clientId)))
      } catch {
        case e: Throwable =>
          log.error(s"Exception creating simple consumer for ${b.host}: ${e.getMessage}")
          consumersByHost.put(b.host, None)
      }
    }

    refreshPartitionLeaders()
  }

  def refreshPartitionLeaders() = {
    val topicMetaRequest = new TopicMetadataRequest(
      versionId = 1,
      correlationId = 0,
      clientId = clientId,
      topics = Seq())

    // Get our partition meta data for the configured topics and map the topic/partition tuples to consumers
    var failed = false
    val processedClusters = new mutable.HashSet[String]()
    val clusterSet = clusters.values.toSet
    for (consumer <- consumersByHost.filter(_._2.isDefined)
        if !clusterSet.forall(x => processedClusters.contains(x))
        if !processedClusters.contains(clusters(consumer._1))) {
      try {
        val topicsMetaResp = consumer._2.get.send(topicMetaRequest)

        for ( topicMeta <- topicsMetaResp.topicsMetadata.filter { meta => configuredTopics.contains(meta.topic) };
              partMeta <- topicMeta.partitionsMetadata )
        yield {
          val key = (topicMeta.topic, partMeta.partitionId)
          partMeta.leader match {
            case Some(broker) =>
              log.info(s"Leader found for topic [${topicMeta.topic}:${partMeta.partitionId}]: ${broker.host}")
              partitionsByTopic.add(PartitionAssignment(topicMeta.topic, partMeta.partitionId, clusters(broker.host), broker.host))
              if (hostsByTopicParts.contains(key) && hostsByTopicParts.get(key).isDefined) {
                hostsByTopicParts.put(key, Some(hostsByTopicParts.get(key).get.get :+ broker.host))
              } else {
                hostsByTopicParts.put(key, Some(List(broker.host)))
              }
            case None =>
              log.error(s"No leader found for topic [${topicMeta.topic}:${partMeta.partitionId}]")
              hostsByTopicParts.put(key, None)
          }
          processedClusters.add(clusters(consumer._1))
        }
      } catch {
        case e: Throwable =>
          log.error(s"Unable to get topic meta data from ${consumer._2.get.host}, retrying in 10 seconds", e)
          failed = true
      }
    }

    if (hostsByTopicParts.isEmpty || failed || !clusterSet.forall(x => processedClusters.contains(x))) {
      health = HealthComponent("kafka-proxy", ComponentState.DEGRADED, "Failed to fetch broker info from ZK. Retrying in 10 seconds")
      context.system.scheduler.scheduleOnce(10 seconds) { self ! KafkaRefreshReq(hostsByTopicParts.nonEmpty) }
    }
  }

  // We only want to initiate a broker refresh if one has not already been initiated. When one is needed,
  // clear out kafka mappings and kick it off. Clearing out the mappings means that any FetchReq messages received
  // before the refresh has finished will fail. Workers are responsible for handling the failure and retrying
  def refreshBrokerInfoIfNeeded() = {
    brokers match {
      case Some(brokers) =>
        clearKafkaMappings()
        self ! KafkaRefreshReq(false)
      case None =>
        // Do nothing. A refresh is already in flight.
    }
  }

  def clearKafkaMappings() = {
    consumersByHost.foreach { case (host, consumer) =>
      if (consumer.isDefined)
        consumer.get.close()
    }
    consumersByHost.clear()
    hostsByTopicParts.clear()
    partitionsByTopic.clear()
    clusters.clear()
    brokers = None
  }
}
