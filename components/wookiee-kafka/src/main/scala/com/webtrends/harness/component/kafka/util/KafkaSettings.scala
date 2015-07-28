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

package com.webtrends.harness.component.kafka.util

import java.net.InetAddress
import java.nio.charset.StandardCharsets

import akka.actor.Actor
import com.typesafe.config.Config
import com.webtrends.harness.component.kafka.KafkaConsumerCoordinator
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.component.kafka.actor.KafkaTopicManager.BrokerSpec
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.config.ConfigHelper
import com.webtrends.harness.utils.ConfigUtil

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

trait KafkaSettings extends ConfigHelper { this: Actor =>
  @volatile
  var kafkaConfig: Config = ConfigUtil.prepareSubConfig(renewableConfig, "wookiee-kafka")
  val hostname = InetAddress.getLocalHost.getHostName
  val clientId = s"kk_$hostname"
  val bufferSize = 1024*1024

  // Used for ZK paths
  def pod = zkConf.get.dataCenter+"_"+zkConf.get.pod
  def appRootPath = s"/$appName/$pod"

  def distributionRootPath = s"$appRootPath/kafkaConsumerDistribution"

  val utf8 = StandardCharsets.UTF_8

  def distributorPaths = DistributorPaths(s"$distributionRootPath/nodes",
    s"$distributionRootPath/assignments",
    s"$distributionRootPath/leader")

  def offsetGetExpiration = Try { kafkaConfig.getInt("offset-timeout-seconds") } getOrElse 10 seconds
  def zkConf = renewableConfig.hasPath("wookiee-zookeeper.quorum") match {
    case true => Some(ZookeeperSettings(renewableConfig.getConfig("wookiee-zookeeper")))
    case false => None
  }
  def topicWorker = this.getClass.getClassLoader.loadClass(kafkaConfig.getString("worker-class"))
  def leader = {
    if (kafkaConfig.hasPath("leader-class")) {
      this.getClass.getClassLoader.loadClass(kafkaConfig.getString("leader-class"))
    } else {
      classOf[KafkaConsumerCoordinator]
    }
  }
  def appName = kafkaConfig.getString("app-name")

  def init: KafkaSettings = {
    this
  }

  def topicMap: Map[String, Config] = (kafkaConfig.getConfigList("consumer.topics") map { top =>
    top.getString("name") -> top
  }).toMap

  def kafkaSources: Map[String, BrokerSpec] = (kafkaConfig.getConfigList("consumer.kafka-hosts") flatMap { clusters =>
    clusters.getStringList("brokers") map { broker =>
      val hostPort: Array[String] = broker.split(":")
      val host: String = hostPort(0)
      val port: Int = if (hostPort.length == 2) hostPort(1).toInt else 9092
      host -> BrokerSpec(host, port, clusters.getString("id"))
    }
  }).toMap

  def assignmentName(spec:PartitionAssignment): String = {
    s"${spec.host}-${spec.topic}-${spec.partition}"
  }

  def partitionName(spec:PartitionAssignment): String = {
    s"${spec.cluster}-${spec.topic}-${spec.partition}"
  }

  def getAssignmentHost(assign: String): String = {
    assign.split('-')(0)
  }

  override def renewConfiguration() = {
    super.renewConfiguration()
    kafkaConfig = ConfigUtil.prepareSubConfig(renewableConfig, "wookiee-kafka")
  }
}


//Simple container for Paths
case class DistributorPaths(nodePath: String, //Root path for node registration
                            assignmentPath: String,//Root path for assignments per node
                            leaderPath: String) //Leader election directory

