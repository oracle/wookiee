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

package com.webtrends.harness.component.kafka.health

import com.typesafe.config.Config
import com.webtrends.harness.component.kafka.KafkaConsumerCoordinator
import com.webtrends.harness.component.kafka.actor.KafkaTopicManager.DownSources
import com.webtrends.harness.health
import com.webtrends.harness.health.ComponentState._
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth

import scala.collection.mutable

trait WorkerHealthState {
  def name: String
  def healthy: Boolean
  def status: String
}
case class KafkaHealthState(name: String, healthy: Boolean, status: String, topic: String) extends WorkerHealthState
case class ZKHealthState(name: String, healthy: Boolean, status: String) extends WorkerHealthState

trait CoordinatorHealth { this: KafkaConsumerCoordinator =>

  // We need to keep track of the health of our partition workers. Each worker will notify the leader
  // of the status when a kafka fetch is performed. We can not have each individual worker
  // keep track of its own health because asking for that health will timeout if the worker is processing
  // a message which takes a significant amount of time
  val workerKafkaHealth = mutable.Map[String, (WorkerHealthState, Long, String)]()
  val workerZKHealth = mutable.Map[String, WorkerHealthState]()
  // Holds millis allowed before we say that a worker is starved
  var topicAgeThresholds = mutable.Map[String, Long]()
  var downSources = Set[String]()

  // We need to report our health status as critical if the events being processed are too old.
  // There are a few scenarios where this could occur:
  //
  // 1. There is an issue copying data from one or more servers (or whole data centers.)
  //
  // 2. We are backed up. This is the scenario where harness is down for a while or it can't process data fast enough.
  //    Its expected that all partitions will be backed up in this case, but its possible that only some partitions are
  //
  // To address these scenario we will be storing and reporting the partitions per topic and collection server that have
  // old events.
  //
  // If we are in scenario 1, we would see the troubled server(s) listed with all partitions called out.
  //
  // If we are in scenario 2, we will see all servers listed with the backed up partition(s) listed.
  val criticalPartsByTopicAndServer = mutable.Map[String, mutable.Map[String, Set[Int]]]()

  def healthReceive: Receive = {
    case CheckHealth =>
      try {
        sender ! aggregateHealthStates()
      } catch {
        case ex: Exception => sender ! HealthComponent("Coordinator Health",
          ComponentState.CRITICAL, s"Could not get health: ${ex.getMessage}")
      }

    case msg: KafkaHealthState =>
      if (msg.topic.isEmpty && workers.contains(msg.name)) {
        workers(msg.name).started = false
      } else {
        workerKafkaHealth.put(msg.name, (msg, System.currentTimeMillis(), msg.topic))
      }

    case msg: ZKHealthState =>
      workerZKHealth.put(msg.name, msg)

    case msg: DownSources => downSources = msg.sources
  }

  def eventAgeHealthByServer(): List[HealthComponent]  = {
    (for ((topic, partitionsByServer) <- criticalPartsByTopicAndServer) yield {
      val serverHealths = for ( (server, partitions) <- partitionsByServer) yield {
        HealthComponent(name = s"$server",
          state = ComponentState.CRITICAL,
          details = s"Lagging events found in partition(s) ${partitions.toList.sorted.mkString(",")}")
      }

      HealthComponent(name = s"$topic: Servers exceeding age threshold",
        state = ComponentState.CRITICAL,
        details = s"Data from ${serverHealths.size} server(s) exceeded the age threshold",
        components = serverHealths.to[List]
      )
    }).to[List]
  }

  def renewTopicAgeThresholds() {
    topicAgeThresholds.clear()
    topicMap foreach { case (s: String, c: Config) =>
      topicAgeThresholds(s) = c.hasPath("event-age-threshold-seconds") match {
        case true => c.getLong("event-age-threshold-seconds") * 1000
        case false => 0
      }
    }
  }

  def listActiveWorkers: List[String] = {
    workers.filter(entry => entry._2.started).map(entry => entry._1).toList
  }

  def aggregateHealthStates(): HealthComponent = {
    val workerList = listActiveWorkers
    mergeHealths(name = "Coordinator Health",
      desc = "Aggregate coordinator health",
      subComponents = List(
        collapseWithTimeCheck(workerKafkaHealth.toMap, "Kafka Health"),
        collapseHealthStates(workerZKHealth.toMap, "Zookeeper Health"),
        HealthComponent("Active Worker List", ComponentState.NORMAL, s"Workers = ${workerList.size}", Some(workerList.mkString(", "))))
        ++ eventAgeHealthByServer()
    )
  }

  def collapseWithTimeCheck(healths: Map[String, (WorkerHealthState, Long, String)], label: String): HealthComponent = {
    val filteredHealths = healths.filter { h =>
      workers.contains(h._1) && (workers(h._1).started || (!workers(h._1).started && !h._2._1.healthy))
    }

    var unHealthy = filteredHealths.filter { h => !h._2._1.healthy && !downSources.contains(getAssignmentHost(h._2._1.name)) }.map { h: (String, (WorkerHealthState, Long, String)) =>
      HealthComponent(name = h._2._1.name,
        state = ComponentState.CRITICAL,
        details = h._2._1.status)
    }.toList
    val time = System.currentTimeMillis()
    unHealthy = unHealthy ++ filteredHealths.filter { h =>
      h._2._1.healthy && topicAgeThresholds(h._2._3) > 0 && (time - h._2._2) > topicAgeThresholds(h._2._3) && !downSources.contains(getAssignmentHost(h._2._1.name))
    }.map {
      h: (String, (WorkerHealthState, Long, String)) =>
        HealthComponent(name = h._2._1.name,
          state = ComponentState.DEGRADED,
          details = s"No events in ${(time - h._2._2) / 1000} seconds")
    }.toList
    aggregatedComponent(filteredHealths.toMap, label, unHealthy)
  }

  def collapseHealthStates(healths: Map[String, WorkerHealthState], label: String): HealthComponent = {
    val unHealthy = healths.filterNot { h => h._2.healthy }.map { h: (String, WorkerHealthState) =>
      HealthComponent(name = h._2.name,
        state = ComponentState.CRITICAL,
        details = h._2.status)
    }.toList
    aggregatedComponent(healths.toMap, label, unHealthy)
  }

  def aggregatedComponent(healths: Map[String, Any], label: String, unHealthy: List[HealthComponent]): HealthComponent = {
    HealthComponent(name = label,
      state = if (unHealthy.isEmpty) ComponentState.NORMAL else ComponentState.DEGRADED,
      details = s"${healths.size - unHealthy.size}/${healths.size} workers are healthy",
      components = unHealthy)
  }

  def mergeHealths(name: String, desc: String = "", subComponents: List[HealthComponent]): HealthComponent = {
    def worstState(): ComponentState = {
      val states = subComponents.map { subComp => subComp.state }
      states.sortWith{(lt: ComponentState, gt: ComponentState) =>
        if (lt == ComponentState.CRITICAL) true
        else if (lt == ComponentState.DEGRADED && gt == ComponentState.NORMAL) true
        else false
      }.head
    }

    health.HealthComponent(name = name,
      state = worstState(),
      details = desc,
      components = subComponents.to[List])
  }
}
