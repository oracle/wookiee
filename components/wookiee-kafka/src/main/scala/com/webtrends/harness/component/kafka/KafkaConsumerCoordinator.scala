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

package com.webtrends.harness.component.kafka

import akka.actor._
import akka.util.Timeout
import com.webtrends.harness.app.HarnessActor.{ConfigChange, PrepareForShutdown}
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.component.kafka.actor.KafkaTopicManager.DownSources
import com.webtrends.harness.component.kafka.actor.PartitionConsumerWorker._
import com.webtrends.harness.component.kafka.actor.{HostList, AssignmentDistributorLeader, OffsetManager, PartitionConsumerWorker}
import com.webtrends.harness.component.kafka.health.CoordinatorHealth
import com.webtrends.harness.component.kafka.util.KafkaSettings
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.{ZookeeperChildEvent, ZookeeperChildEventRegistration}
import com.webtrends.harness.component.zookeeper.ZookeeperEventAdapter
import com.webtrends.harness.logging.ActorLoggingAdapter
import net.liftweb.json.Serialization
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._

import scala.collection.mutable
import scala.concurrent.duration._

object KafkaConsumerCoordinator {

  def props(sourceProxy: ActorRef, sourceMonitor: Option[ActorRef] = None) =
    Props(new KafkaConsumerCoordinator(sourceProxy, sourceMonitor))

  case class NodeData(nodeId: String, data: String)
  case class TopicPartitionResp(partitionsByTopic: Set[PartitionAssignment])
  case class BroadcastToWorkers(msg: Any)
}

class KafkaConsumerCoordinator(kafkaProxy: ActorRef, sourceMonitor: Option[ActorRef] = None) extends Actor
with ActorLoggingAdapter
with CoordinatorHealth
with KafkaSettings
with ZookeeperEventAdapter {

  import AssignmentDistributorLeader._
  import KafkaConsumerCoordinator._

  implicit val timeout = Timeout(10 seconds)
  implicit val system = context.system

  val offsetManager = context.actorOf(OffsetManager.props(appRootPath, offsetGetExpiration), "offset-manager")

  val workers = mutable.Map.empty[String,WorkerRef]

  override def preStart() {
    register(self, ZookeeperChildEventRegistration(self, distributorPaths.assignmentPath))
    renewTopicAgeThresholds()
  }

  override def postStop() {
    unregister(self, ZookeeperChildEventRegistration(self, distributorPaths.assignmentPath))

    context.children foreach { child ⇒
      log.warning(s"Stopping child [${child.path}]")
      context.unwatch(child)
      context.stop(child)
    }
  }

  def receive = initial

  def initial:Receive = healthReceive orElse configReceive orElse {
    case NodeData(nodeId, data) =>
      val assignments = Serialization.read(data)(formats, manifest[List[PartitionAssignment]])
      sourceMonitor foreach (_ ! HostList(assignments.map(_.host)))
      processAssignmentEvent(assignments)

    // ZK data has changed
    case ZookeeperChildEvent(event) =>
      // Node registration change
      event.getType match {
        // Node data has changed. If the node is local, notify the node actor
        case CHILD_UPDATED | CHILD_ADDED | CHILD_REMOVED =>
          val nodeId = event.getData.getPath.split("/").last
          if (hostname == nodeId) {
            log.debug(s"Node data for [$nodeId] has been updated. Notifying it")
            self ! NodeData(nodeId, new String(event.getData.getData, utf8))
          }

        case _ =>
        // To remove a compiler warning
      }

    case PrepareForShutdown =>
      //Stop all workers
      stopAllWorkers()
      context.become(awaitingShutdown)

    case BroadcastToWorkers(msg) =>
      workers withFilter(_._2.started) foreach(_._2.actor.forward(msg))
  }

  /**
   * We got the prepare for shutdown event
   * @return
   */
  def awaitingShutdown:Receive = healthReceive orElse configReceive orElse {
    case msg:Any => //Just ignore all other messages
      log.debug(s"Waiting for shutdown ignoring $msg")
  }


  def stopAllWorkers() = {
    log.info(s"Stopping ${workers.size} workers")
    workers foreach(_._2.stopWorker())
  }

  override def renewConfiguration() = {
    super.renewConfiguration()
    kafkaProxy ! ConfigChange()
    renewTopicAgeThresholds()
  }

  // Will stop all actors that are no longer needed and start any that are now needed
  def stopUnneededWorkers(assigns: List[PartitionAssignment]) {
    val specs = assigns.map(_.assignmentName)
    workers foreach { worker =>
      if (specs.contains(worker._1)) {
        worker._2.startWorker()
      } else {
        worker._2.stopWorker()
        workerKafkaHealth.remove(worker._1)
      }
    }
  }

  def createWorkerIfNeeded(assign: PartitionAssignment) {
    val spec = assign.assignmentName
    if (!workers.contains(spec)) {
      log.info(s"Creating worker ${assign.host}, ${assign.topic}_${assign.partition}")
      val ref = context.actorOf(Props(topicWorker, kafkaProxy, assign, offsetManager).withDispatcher("worker-dispatcher"), spec)
      workers.put(spec, new WorkerRef(true, ref))
    }
  }

  def processAssignmentEvent(assigns: List[PartitionAssignment]) {
    log.debug(s"Processing ${assigns.length} assignments.")
    stopUnneededWorkers(assigns)
    assigns foreach { assign => createWorkerIfNeeded(assign) }
  }

  // Used to keep track of workers and their states, will start on construction if start=true
  class WorkerRef(start: Boolean, ref: ActorRef) {
    var started = start
    val actor = ref
    if (started) {
      startWorker()
    }

    override def toString = s"WorkerRef(started=$started, actor=$actor)"

    def stopWorker() {
      ref ! PartitionConsumerWorker.Stop
      started = false
    }

    def startWorker() {
      ref ! Start
      started = true
    }
  }
}
