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
import com.webtrends.harness.app.HarnessActor.PrepareForShutdown
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.RefreshNodeAssignments
import com.webtrends.harness.component.kafka.util.KafkaSettings
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.{ZookeeperChildEvent, ZookeeperChildEventRegistration, ZookeeperStateEvent, ZookeeperStateEventRegistration, _}
import com.webtrends.harness.component.zookeeper.{ZookeeperAdapter, ZookeeperEventAdapter}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.CheckHealth
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
import org.apache.curator.framework.state.ConnectionState._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object KafkaConsumerDistributor {
  def props(sourceProxy: ActorRef) =
    Props(new KafkaConsumerDistributor(sourceProxy))

  //Registration messages
  case object RegisterSelf
  case class NodeRegistrationResult(success: Boolean)
  
  //Leader messages
  case object GetAssignmentLeader
  case class AssignmentLeaderResult(leader: Option[ActorRef])
  
  // Node messages
  case object GetRegisteredNodes
  case object GetNodeAssignments

  case class RegisteredStatus(registered: Boolean)
  case class RegisteredNodes(data: Option[List[String]])
  case class NodeAssignments(data: Option[Map[String, String]])
}

class KafkaConsumerDistributor(sourceProxy: ActorRef) extends Actor
  with ActorLoggingAdapter
  with ZookeeperAdapter
  with ZookeeperEventAdapter
  with KafkaSettings
  with Stash {

  import KafkaConsumerDistributor._

  implicit val timeout = Timeout(5 seconds)

  //Registration zk path
  val zkNodePath = s"${distributorPaths.nodePath}/$hostname"
  val hcName = "consumer-distributor"
  var lastOutOfSyncUpdate = 0L
  var assignmentLeader: Option[ActorRef] = None


  override def preStart() {
    // Register for ZooKeeper State events. When registering we will be sent a message
    // for the current state
    register(self, ZookeeperStateEventRegistration(self))
    register(self, ZookeeperChildEventRegistration(self, distributorPaths.nodePath))
    register(self, ZookeeperLeaderEventRegistration(self, distributorPaths.leaderPath))

    self ! RegisterSelf
  }

  def shutdown() {
    unregisterNode()
    unregister(self, ZookeeperStateEventRegistration(self))
    unregister(self, ZookeeperChildEventRegistration(self, distributorPaths.nodePath))
    unregister(self, ZookeeperLeaderEventRegistration(self, distributorPaths.leaderPath))
  }

  def receive = initializing

  /**
   * Initializing the Distributor, if we can't then
   * we will schedule a message to keep trying
   * @return
   */
  def initializing: Receive = {
    case RegisterSelf => registerNode()

    case NodeRegistrationResult(result) =>
      //If we can't register, we keep trying
      result match {
        case true =>
          log.info(s"$hostname registered!, moving to registered state")
          context.become(registered)
          unstashAll()
        case false =>
          context.system.scheduler.scheduleOnce(3 seconds,
            self,
            RegisterSelf)
      }

    case CheckHealth => sender() ! HealthComponent(hcName,
                                                    ComponentState.DEGRADED,
                                                    "Distributor has not registered with ZK yet")

    case PrepareForShutdown => shutdown()

    case msg => stash()
  }

  /**
   * Once we are registered, we can accept other messages
   * @return
   */
  def registered: Receive = {
      // Our ZK state has changed
      case ZookeeperStateEvent(state) =>
        state match {
          // If our ZK state is flaky, we may need to reestablish the local node registrations
          case conn @ (CONNECTED | RECONNECTED) =>
            log.info(s"Moving back to initializing due to Zk connection State =${conn} ")
            context.become(initializing)
            self ! RegisterSelf
          case _ =>
            log.debug(s"Zk state event=${state}")
            relinquishLeadership()
        }

      // ZK data has changed
      case ZookeeperChildEvent(event) =>
        handleChildEvent(event)

      // A change in leadership has occurred
      case ZookeeperLeadershipEvent(leader) =>
        if (leader) takeLeadership() else relinquishLeadership()

      case GetRegisteredNodes =>
        getRegisteredNodes(sender())

      case GetNodeAssignments =>
        getNodeAssignments(sender())

      case GetAssignmentLeader => sender ! AssignmentLeaderResult(assignmentLeader)

      case PrepareForShutdown => shutdown()

      case CheckHealth =>
        val msg = assignmentLeader.map(_ => "LEADER - ")
                                  .getOrElse("") + "Distributor Registered with Zk"

        sender() ! HealthComponent(hcName, ComponentState.NORMAL, msg)

      case msg => log.debug(s"Unknown message $msg")
  }

  def registerNode() = {
    log.debug(s"Registering node: $hostname")
    // When the service is restarted, the ephemeral node from the previous process may not have been deleted yet.
    // Make sure it has been deleted before attempting to add the new one.
    deleteNode(zkNodePath).onComplete {
     case _ =>
       log.debug(s"Node deleted, $zkNodePath!")
       setData(zkNodePath, hostname.getBytes(utf8), create = true, ephemeral = true).onComplete {
         case Success(data) =>
            log.info(s"Successfully initialized node registration for node [$hostname]")

            self ! NodeRegistrationResult(true)
          case Failure(fail) =>
            log.error(s"Unable to initialize node registration for node [$hostname]. ${fail.getMessage()}")
            self  ! NodeRegistrationResult(false)
        }
    }
  }

  def unregisterNode() = {
    deleteNode(zkNodePath).onComplete {
      case Success(path) =>
        log.info(s"Successfully removed node registration for node [$hostname]")

      case Failure(fail) =>
        log.error(s"Unable to remove node registration for node [$hostname]. ${fail.getMessage()}")
    }
  }

  def getRegisteredNodes(requestingActor: ActorRef) = {
    getChildren(s"${distributorPaths.nodePath}", includeData = false).onComplete {
      case Success(nodes) =>
        requestingActor ! RegisteredNodes(Some(nodes.map{node => node._1}.toList))
      case Failure(fail) =>
        requestingActor ! RegisteredNodes(None)
        log.error(s"Not requesting node data update because " +
          s"I am unable to get list of nodes at ${distributorPaths.nodePath}. ${fail.getMessage()}")
    }
  }

  def getNodeAssignments(requestingActor: ActorRef) = {
    log.debug(s"Fetching assigments for ${distributorPaths.assignmentPath}")
    getChildren(s"${distributorPaths.assignmentPath}", includeData = true).onComplete {
      case Success(nodes) =>
        requestingActor ! NodeAssignments(Some(nodes.map { node => node._1 -> new String(node._2.get, utf8) }.toMap))
      case Failure(fail) =>
        log.error(s"Unable to get list of nodes at $appRootPath. ${fail.getMessage()}")
        requestingActor ! NodeAssignments(None)
    }
  }

  def takeLeadership() = {
    assignmentLeader.fold({
      log.info(s"Taking leadership of ${distributorPaths.leaderPath}")
      val leaderActor = context.actorOf(AssignmentDistributorLeader.props(sourceProxy), "assignment-leader")
      assignmentLeader = Some(leaderActor)
      leaderActor ! RefreshNodeAssignments
    })( _ => log.info(s"Asked to take leadership when I am already the leader"))
  }

  def relinquishLeadership() = {
    assignmentLeader.foreach { actor =>
      log.info(s"Relinquishing leadership of ${distributorPaths.leaderPath}")
      context.stop(_)
    }
    assignmentLeader = None
  }

  def handleChildEvent(event: PathChildrenCacheEvent) = {
    val eventPath = event.getData.getPath

    // Node registration change
    if (eventPath.startsWith(distributorPaths.nodePath)) {
      event.getType match {
        case CHILD_ADDED | CHILD_REMOVED =>
          assignmentLeader.foreach { leader =>
            log.info("Node child registration changed")
            val now = System.currentTimeMillis()
            // Limit churn caused by other nodes restarting or being in a wonky state.
            if (now > lastOutOfSyncUpdate + 30000) {
              lastOutOfSyncUpdate = now
              leader ! RefreshNodeAssignments
            }
          }

        case _ =>
        // To remove a compiler warning
      }
    }
  }
}
