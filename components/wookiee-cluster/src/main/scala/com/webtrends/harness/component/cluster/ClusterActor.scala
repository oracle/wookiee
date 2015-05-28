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
package com.webtrends.harness.component.cluster

import java.net.InetAddress

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, MemberStatus}
import akka.remote.QuarantinedEvent
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.component.zookeeper.{NodeRegistration, ZookeeperEventAdapter, ZookeeperAdapter}
import com.webtrends.harness.health.{ActorHealth, ComponentState, HealthComponent}
import com.webtrends.harness.logging.ActorLoggingAdapter
import net.liftweb.json.parseOpt

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

object ClusterActor {
  def props()(implicit system: ActorSystem): Props = Props[ClusterActor]
    .withDispatcher(new akka.cluster.ClusterSettings(system.settings.config, system.name).UseDispatcher)

  @SerialVersionUID(1L) case class LeaveCluster()

  @SerialVersionUID(1L) case class ClusterLeft()

  @SerialVersionUID(1L) case class RejoinCluster(auto: Boolean)

  @SerialVersionUID(1L) case class ClusterJoined()

  @SerialVersionUID(1L) case class ClusterJoinFailure()

  @SerialVersionUID(1L) case class GetClusterState()

}

class ClusterActor extends Actor
    with ActorLoggingAdapter
    with ActorHealth
    //with MetricsAdapter
    with ClusterStateSerializer
    with ZookeeperAdapter
    with ZookeeperEventAdapter {

  import com.webtrends.harness.component.cluster.ClusterActor._
  import context.dispatcher

  //private val rejoinCounter = Counter("harness.cluster-service.manual-rejoin")
  //private val autoCounter = Counter("harness.cluster-service.auto-rejoin")

  private val akkaProvider = context.system.settings.config.getString("akka.actor.provider")
  val zookeeperSettings = ZookeeperSettings(context.system.settings.config.getConfig("wookiee-zookeeper"))
  val clusterSettings = ClusterSettings(context.system.settings.config.getConfig("wookiee-cluster"), akkaProvider)

  val leaderPath = s"${NodeRegistration.getBasePath(context.system.settings.config)}/leader"
  val membersPath = s"${NodeRegistration.getBasePath(context.system.settings.config)}/nodes"

  val cluster = Cluster(context.system)
  val selfAddress = cluster.selfAddress

  var leader = false
  val clusterJoinTimeout = 15 seconds

  // The properties for capturing cluster state
  var balancedCluster = true
  var stateCheck: Option[Cancellable] = None
  val selfStateInterval: FiniteDuration = 2 seconds
  val clusterStateInterval: FiniteDuration = 5 seconds
  val maxRetries = 3

  override def preStart(): Unit = {
    try {
      log.info("Cluster manager starting: {}", context.self.path)
      joinCluster
      context.system.eventStream.subscribe(self, classOf[QuarantinedEvent])
      log.info("Cluster manager started: {}", context.self.path)
    }
    catch {
      case e: Throwable =>
        log.error("FATAL: Exception when trying to join the cluster", e)
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info("Cluster manager restarting: {}", context.self.path)
    // Don't call postStop because we don't want to leave the cluster
    // since we are just restarting
  }

  override def postStop(): Unit = {
    Try({
      if ( stateCheck.isDefined)  stateCheck.get.cancel
      context.system.eventStream.unsubscribe(self)

      if (!cluster.isTerminated) {
        cluster.unsubscribe(self)
        // Mark us as leaving if we have not already done so
        if (cluster.state.members.exists(m ⇒ m.address == selfAddress && m.status == MemberStatus.Up)) {
          log.info("Leaving the cluster")
          cluster.leave(selfAddress)
        }
      }

      log.info("Cluster manager stopped: {}", context.self.path)
    }).recover({
      case e: Throwable =>
        log.warning("Error shutting down the clustering: {}", e.getMessage)
    })
  }

  def receive = health orElse {
    // ---- Internal Specific ----
    case RejoinCluster(auto) if (cluster.state.members.filter(m => !auto || (m.address == selfAddress))).size > 0 =>
      rejoinCluster(auto)

    // Leave the cluster
    case LeaveCluster => leaveCluster
    // Request for JSON format of the current cluster state
    case GetClusterState => sender ! serializeState(cluster.state)

    // ---- Cluster Specific ----
    // Process the change of leader in the cluster
    case LeaderChanged(leaderOption) => leaderChanged(leaderOption)

    // If we were the node to have exited then un-subscribe from cluster events and tell our parent that we have left the cluster
    case MemberExited(member) if member.address == selfAddress =>
      log.info("We have exited the cluster")
      context become shuttingDown
      context.parent ! ClusterLeft

    case MemberRemoved(member, stat) if member.address == selfAddress =>
      // If we are here then we have been removed from the cluster without going through the
      // exiting process. This means that some catastrophic event occured which requires us to restart
      // the actor system
      log.warn("We have been removed the cluster without our consent. We shall restart the system.")
      rejoinCluster(true)

    // If we have received a quarantine event and we are the leader then it might be because we have been isolated and need to restart
    case QuarantinedEvent(address, uid) if leader && cluster.state.members.size == 1 =>
      log.warn("We have been quarantined. We shall restart the system")
      rejoinCluster(true)

    case _ => // Do nothing

  }

  /**
   * When shutting down, either by choice or forcefully, we replace the default handler with this.
   * @return
   */
  def shuttingDown: Receive = {
    // Leave the cluster
    case LeaveCluster => leaveCluster
    // If we were the node to have exited then un-subscribe from cluster events and tell our parent that we have left the cluster
    case MemberExited(member) if member.address == selfAddress =>
      log.info("We have exited the cluster")
      context.parent ! ClusterLeft
  }

  /**
   * This method is called when the system is first started and is responsible for joining an existing cluster
   */
  private def joinCluster: Unit = {
    // If the cluster is not terminated and we are not a current member then join
    if (!cluster.isTerminated && !cluster.state.members.exists(_.address == selfAddress)) {
      createNode(membersPath, false, None)(clusterJoinTimeout) onComplete {
        case Success(path) =>
          getChildren(membersPath, true)(clusterJoinTimeout).mapTo[Seq[(String, Option[Array[Byte]])]] onComplete {
            case Success(nodes) =>
              if (nodes == Nil || nodes.size == 1) {
                log.info("Joining the cluster as self at {}", selfAddress)
                cluster.join(selfAddress)
              }
              else {
                val host = InetAddress.getLocalHost.getCanonicalHostName
                val protocol = selfAddress.protocol
                val root = selfAddress.system
                val local = s"${host}:${selfAddress.port.get}"

                // Get the nodes to seed with and include our self first
                val count = if (nodes.length >= clusterSettings.randomSeedNodes) clusterSettings.randomSeedNodes else nodes.length

                def getNodes(size: Int): Seq[String] = {
                  for {
                  // Take some extras since we are weeding out any that are not hooked to the cluster
                    node <- Random.shuffle(nodes.take(size * 5))
                    name = node._2 match {
                      case Some(bytes) =>
                        val data =  new String(bytes)
                        parseOpt(data) match {
                          case Some(json) =>
                            if ((json \ "cluster-enabled").extract[Boolean])
                              Some(node._1)
                            else
                              None
                          case None => Some(node._1)
                        }
                      case None => Some(node._1)
                    }
                    if (name.isDefined)
                  } yield name.get
                }

                val sub = (Seq(local) ++ getNodes(count)).distinct.take(count)
                log.info(s"Seed nodes from Zookeeper are: ${sub.mkString(",")}")

                val adds = sub.map {
                  node =>
                    val address = AddressFromURIString(s"${protocol}://${root}@${node}")

                    if (address.host.get.equalsIgnoreCase(host)) {
                      // If this is this machine then the value is full qualified and we shall replace with what are akka
                      // settings list us as
                      Address(protocol, root, selfAddress.host.get, address.port.get)
                    }
                    else {
                      address
                    }
                }.toIndexedSeq

                if (adds == Nil) {
                  log.info("Joining the cluster as self at {}", selfAddress)
                  cluster.join(selfAddress)
                }
                else {
                  log.info(s"Joining the cluster with the seed nodes ${adds.mkString(",")}")
                  cluster.joinSeedNodes(adds)
                }
              }

              // Subscribe to events
              cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot,
                classOf[LeaderChanged], classOf[ReachabilityEvent], classOf[MemberEvent])

              // Initialize the state interval
              stateCheck = Some(context.system.scheduler.scheduleOnce(selfStateInterval) { validateClusterMembership })

              // Send a message to the parent that we have joined the cluster
              context.parent ! ClusterJoined

            case Failure(e) =>
              log.error("An error occurred while trying to fetch the cluster seed nodes from Zookeeper", e)
              context.parent ! ClusterJoinFailure
          }

        case Failure(e) =>
          log.error("An error occurred while trying to create the root path in Zookeeper", e)
          context.parent ! ClusterJoinFailure
      }
    }
  }

  /**
   * This method will force us to rejoin the cluster
   * @param auto
   */
  private def rejoinCluster(auto: Boolean): Unit = {
    if (stateCheck.isDefined) {
      stateCheck.get.cancel
      stateCheck = None
    }

    // Swap the message handler first
    context become shuttingDown

    log.info("We have been asked to rejoin the cluster, which at this time requires us to restart the actor system")
    if (auto) {
      //autoCounter.incr
    }
    else {
      //rejoinCounter.incr
    }

    //HarnessServiceSystem.restartActorSystem
  }

  /**
   * This method will tell the cluster that we are leaving.
   */
  private def leaveCluster: Unit = {
    if (cluster.isTerminated) {
      context.parent ! ClusterLeft
    }
    else {
      log.info("Leaving the cluster")
      cluster.leave(selfAddress)
    }
  }

  /**
   * Process the change of leader
   * @param leaderOption The new leader
   */
  private def leaderChanged(leaderOption: Option[Address]): Unit = {
    log.info("Cluster leader has changed and the new leader is this node: {}", leaderOption.exists(_ == selfAddress))
    leader = leaderOption.exists(_ == selfAddress)
    validateCluster(maxRetries, Nil)
  }

  /**
   * This method is called to validate our status within the clut
   */
  private def validateClusterMembership: Unit = {

    // The live members is the difference between all members and those that are currently unreachable
    val live = (cluster.state.members -- cluster.state.unreachable)

    // If there were previously more then one node in the cluster we need to make sure we have not
    // been stranded.
    if ((cluster.state.members.size == 1 && cluster.state.members.head.address == selfAddress) ||
      (live.size == 1 && live.head.address == selfAddress)) {
      log.info("Verifying the cluster since we are the only node.")
      getChildren(membersPath)(5 seconds).mapTo[Seq[(String, Option[Array[Byte]])]] onComplete {
        case Success(nodes) =>
          val clusterNodes = getActiveClusterNodes(nodes)
          if (clusterNodes == Nil || clusterNodes.size == 1) {
            // We are truly the only one so we can move on
            log.info("We are truly the only one according to Zookeeper so we can move on")
          }
          else {
            log.info(s"Cluster nodes from Zookeeper are: ${clusterNodes.mkString(",")}")
            log.warning("I have been stranded so now we will now rejoin the cluster")
            self ! RejoinCluster(true)
          }
        case Failure(e) =>
          log.error("An error occurred while trying to fetch the cluster nodes from Zookeeper while validating membership.", e)
          // Reschedule the check
          stateCheck = Some(context.system.scheduler.scheduleOnce(selfStateInterval) { validateClusterMembership })
      }
    }
    else {
      // Reschedule the check
      stateCheck = Some(context.system.scheduler.scheduleOnce(selfStateInterval) { validateClusterMembership })
    }
  }

  /**
   * This method will check the cluster's balance and is called on leadership changes. If the
   * cluster is deemed unbalanced then a job will be scheduled to recheck it until is is balanced again.
   */
  private def validateCluster(retries: Int, nodesToCheck: Seq[String]): Unit = {
    // Make sure we are the leader
    if (leader) {
      val nextInterval = (retries / maxRetries) * 30 * clusterStateInterval

      log.info("Verifying the cluster since we have become the leader or a previous validation failed. Checking against zookeeper")
      getChildren(membersPath)(5 seconds).mapTo[Seq[(String, Option[Array[Byte]])]] onComplete {
        case Success(nodes) =>
          val clusterNodes = getActiveClusterNodes(nodes)

          if (clusterNodes != Nil && clusterNodes.size > cluster.state.members.size) {
            // The number of cluster nodes in zookeeper does not match that of the current live cluster state

            // This may be a valid situation due to nodes that are actually shutting down properly. There might be a small window
            // when this triggers during a node shutdown due to the timing of the nodes shutdown and removal from zookeeper. Service
            // crashes may trigger this more often since it is unpredictable on when the cluster and zookeeper is spread around
            // the cluster.
            val currentMissing = clusterNodes.diff(cluster.state.members.map(m => m.address.hostPort.substring(m.address.system.length + 1)).toSeq)

            nodesToCheck.size match {
              case 0 => // The difference is what is in zk and not in the cluster
                log.info("The state of cluster nodes in zookeeper shows that there are more registered then in the cluster. We will retry in a moment")
                balancedCluster = false
                context.system.scheduler.scheduleOnce(nextInterval) { validateCluster(retries - 1, currentMissing) }
              case _ => // The missing are the nodes to check that are still missing
                val newNodes = nodesToCheck.diff(currentMissing)

                newNodes.size match {
                  case 0 => // The nodes we are watching are now ok so we can stop
                    balancedCluster = true
                    log.info("The cluster appears to be in order")
                  case _ => // We still have nodes to watch so lets send them back
                    if (retries <= 1) {
                      balancedCluster = false
                      log.warn("The following nodes are suspect because they are registered in Zookeeper, but are not part of the cluster: {}", newNodes.mkString(","))
                      newNodes.foreach(n => sendRejoinMessage(AddressFromURIString(s"${selfAddress.protocol}://${selfAddress.system}@${n}")))
                    }
                    else {
                      balancedCluster = false
                      context.system.scheduler.scheduleOnce(nextInterval) { validateCluster(retries - 1, newNodes) }
                    }
                }
            }
          }
          else {
            balancedCluster = true
            log.info("The cluster appears to be in order")
          }

        case Failure(e) =>
          log.error("An error occurred while trying to fetch the cluster nodes from Zookeeper while validating the cluster. Retrying", e)
          // Setup a job that check the balance again
          context.system.scheduler.scheduleOnce(clusterStateInterval) { validateCluster(retries, nodesToCheck) }
      }
    }
  }

  /**
   * Filter out the active cluster nodes from all nodes
   * @param nodes all nodes registered in Zookeeper
   * @return a sequence of active cluster nodes
   */
  private def getActiveClusterNodes(nodes: Seq[(String, Option[Array[Byte]])]): Seq[String] = {
    nodes flatMap {
      node =>
        if (node._2.isDefined) {
          val data =  new String(node._2.get)
          parseOpt(data) match {
            case Some(json) =>
              if ((json \ "cluster-enabled").extract[Boolean]) Seq(node._1) else None
            case None => Seq(node._1)
          }
        }
        else {
          Seq(node._1)
        }
    }
  }

  /**
   * This method will send a remote node a message to rejoin the cluster.
   * @param address The remote address to send the message to
   */
  private def sendRejoinMessage(address: Address): Unit = {
    // Ask the node to re-join the cluster. If the node is not available then it will simply
    // drop the message
    val path = RootActorPath(address) / "user" / "system" / "cluster"
    context.actorSelection(path) ! RejoinCluster(true)
  }


  /**
   * This is the health of the current object, by default will be NORMAL
   * In general this should be overridden to define the health of the current object
   * For objects that simply manage other objects you shouldn't need to do anything
   * else, as the health of the children components would be handled by their own
   * CheckHealth function
   *
   * @return
   */
  override protected def getHealth: Future[HealthComponent] = {
    Future {
      if (cluster.isTerminated) {
        HealthComponent("cluster", ComponentState.CRITICAL, "The cluster is currently terminated")
      }
      else if (!balancedCluster) {
        HealthComponent("cluster", ComponentState.DEGRADED,
          "The cluster is potentially fragmented which means that there are fewer nodes in the cluster then defined in Zookeeper. If this continues to alert then it warrants a review")
      }
      else {
        HealthComponent("cluster", ComponentState.NORMAL,
          s"The cluster is currently running and we are ${
            leader match {
              case false => "not "
              case _ => ""
            }
          }the leader")
      }
    }
  }
}



