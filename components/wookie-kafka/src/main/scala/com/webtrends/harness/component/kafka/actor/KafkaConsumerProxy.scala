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
import com.webtrends.harness.component.kafka.KafkaConsumerCoordinator.TopicPartitionResp
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.component.kafka.util.{KafkaConsumer, KafkaSettings}
import com.webtrends.harness.component.zookeeper.{ZookeeperAdapter, ZookeeperEventAdapter}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.CheckHealth
import kafka.api.TopicMetadataRequest
import kafka.consumer.SimpleConsumer
import net.liftweb.json.{NoTypeHints, Serialization}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps


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

  case class FetchConsumer(host: String)

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

  var brokers = Seq[BrokerSpec]()
  val consumersByHost = new mutable.HashMap[String,KafkaConsumer]()
  val hostsByTopicParts = new mutable.HashMap[(String, Int), Option[List[String]]]()
  val partitionsByTopic = new mutable.HashSet[PartitionAssignment]()
  val clusters = new mutable.HashMap[String, String]()
  var health = HealthComponent("kafka-proxy", ComponentState.NORMAL, "Proxy has not acquired brokers yet")

  override def preStart() = {
    self ! KafkaRefreshReq(light = false)
  }

  def receive:Receive = configReceive orElse {
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

    case FetchConsumer(host) =>
      sender ! consumersByHost.get(host)

    case CheckHealth => sender ! health
  }

  override def renewConfiguration() = {
    super.renewConfiguration()
    self ! KafkaRefreshReq(light = false)
  }

  def requestBrokerInfo() = {
    log.info("Using kafka-hosts to set consumed hosts, {}", kafkaSources)
    var brokerData = Seq[BrokerSpec]()
    clusters.clear()
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
    brokers = newBrokers

    brokers.filter { it => partitionsByTopic.forall { part => part.host != it.host } }.foreach { b =>
      try {
        val oldConsumer = consumersByHost.get(b.host)
        consumersByHost.put(b.host, new KafkaConsumer(b.host, b.port, 15000, bufferSize, clientId))
        oldConsumer.foreach(_.close())
      } catch {
        case e: Throwable =>
          log.error(s"Exception creating simple consumer for ${b.host}: ${e.getMessage}")
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
    for (bro <- brokers
        if !clusterSet.forall(x => processedClusters.contains(x))
        if !processedClusters.contains(clusters(bro.host))) {
      try {
        val consumer = consumersByHost(bro.host)
        val topicsMetaResp = consumer.send(topicMetaRequest)

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
          processedClusters.add(clusters(bro.host))
        }
      } catch {
        case e: Throwable =>
          log.error(s"Unable to get topic meta data from ${bro.host}, retrying in 10 seconds", e)
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
    partitionsByTopic.synchronized {
      partitionsByTopic.isEmpty match {
        case false =>
          clearKafkaMappings()
          self ! KafkaRefreshReq(light = true)
        case true =>
        // Do nothing. A refresh is already in flight.
      }
    }
  }

  def clearKafkaMappings() = {
    hostsByTopicParts.clear()
    partitionsByTopic.clear()
  }
}
