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

package com.webtrends.harness.component.cluster.communication

import akka.ConfigurationException
import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{MemberStatus, Cluster}
import akka.event.EventStream
import akka.pattern.pipe
import java.net.{URLEncoder, URLDecoder}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.webtrends.harness.component.cluster.communication.MessageProcessor.MessagingStarted
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.Internal.{UnregisterSubscriptionEvent, RegisterSubscriptionEvent}
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.{SubscriptionRemovedEvent, SubscriptionAddedEvent}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.CheckHealth

import scala.Predef._
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.Try

/**
 * This is the main message processor
 * User: vonnagyi
 * Date: 3/7/13
 * Time: 1:24 PM
 */

object MessagingActor {

  def props(settings: MessagingSettings): Props =
    props(FiniteDuration(settings.ShareInterval, TimeUnit.MILLISECONDS), FiniteDuration(settings.TrashInterval, TimeUnit.MILLISECONDS))

  def props(shareInterval: FiniteDuration, trashInterval: FiniteDuration): Props = Props(classOf[MessagingActor], shareInterval, trashInterval)
}

class MessagingActor(shareInterval: FiniteDuration, trashInterval: FiniteDuration)
    extends Actor
    with ActorLoggingAdapter
    with Stash {

  import MessageProcessor.Internal._
  import com.webtrends.harness.component.cluster.communication.MessageService._
  import context.dispatcher

  @SerialVersionUID(1L) case class Share()
  @SerialVersionUID(1L) case class InitialShareComplete()
  @SerialVersionUID(1L) case class InitialShareTimeout()
  @SerialVersionUID(1L) case object Unreachable extends MemberStatus
  @SerialVersionUID(1L) case object Reachable extends MemberStatus

  def eventStream: EventStream = context.system.eventStream

  val cluster = Try(Some(Cluster(context.system)))
    .recoverWith({
    case ce: ConfigurationException =>
      log.warning("The config entry for ActorRefProvider is not 'ClusterActorRefProvider'. Not hooked up to any cluster")
      Try(None)
    case e: Exception =>
      log.error("An error occurred trying to hookup the cluster", e)
      Try(None)
  }).get

  val selfAddress = cluster match {
    case Some(clus) => clus.selfAddress
    case None => self.path.address
  }

  var shareTask: Option[Cancellable] = None

  // The list of global addresses
  var nodes: Set[Address] = Set.empty

  // The list of subscribers
  private val registry = (new ConcurrentHashMap[Address, RegistryEntry]() asScala).withDefault(a =>
    RegistryEntry(a, VectorClock(0L, System.currentTimeMillis), true, Map.empty))

  registry += (selfAddress -> RegistryEntry(selfAddress, VectorClock(0L, System.currentTimeMillis), true, Map.empty))

  // The local registry of subscriptions
  private def localVersions = Map(registry.map {
    case (address, entry) => (address -> entry.clock.counter)
  }.toSeq: _*)

  override def preStart(): Unit = {
    super.preStart()

    // Register as a handler
    MessageService.registerMediator(self)
    // Register for cluster information
    if (cluster.isDefined) {
      require(!cluster.get.isTerminated, "Cluster node must not be terminated")
      cluster.get.subscribe(self, initialStateMode = InitialStateAsSnapshot,
        classOf[MemberEvent], classOf[ReachabilityEvent])

      // Set a timeout so that we will not be stuck if there are no other nodes to gossip with
      context.system.scheduler.scheduleOnce(15 seconds, self, InitialShareTimeout)
    }
    else {
      log.info("Clustering is disabled to message handling will only operate locally")
      context.parent ! MessagingStarted
    }

    log.info("Message processor started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    // Cancel our schedule sharing task
    if (shareTask.isDefined) {
      shareTask.get.cancel()
    }

    // Un-register as a handler
    MessageService.unregisterMediator(self)
    if (cluster.isDefined && !cluster.get.isTerminated) {
      // Un-register for cluster information
      cluster.get unsubscribe self
    }

    log.info("Message processor stopped: {}", context.self.path)
  }

  // If clustering is enabled then we need to go through the initialization phase first
  def receive = if (cluster.isDefined) clusterInitializing else standAloneProcessing

  /**
   * If we are not running with the cluster then just handle the basics
   * @return
   */
  def standAloneProcessing: Receive = commonProcessing orElse pubSubProcessing

  /**
   * If we are running in a cluster then we need to make sure we that we have received
   * a gossip share or that we have sent one out to a remote node
   * @return
   */
  def clusterInitializing: Receive = commonProcessing orElse clusterProcessing orElse shareProcessing orElse {
    case InitialShareComplete => // The initial sharing has completed so switch to processing mode
      log.info("Initial share has been completed")
      unstashAll()
      context.become(mainProcessing)
      context.parent ! MessagingStarted
      shareTask = Some(context.system.scheduler.schedule(50 millisecond, shareInterval, self, Share))

    case InitialShareTimeout =>
      log.error("Initial share has timeout which means that there are either no other nodes in the cluster or we have not received a share")
      unstashAll()
      context.become(mainProcessing)
      // Continue on anyways
      context.parent ! MessagingStarted
      shareTask = Some(context.system.scheduler.schedule(50 millisecond, shareInterval, self, Share))

    case msg => stash() // Stash everything else for now
  }


  /**
   * Once the service is initialized then this is the main processing unit
   * @return
   */
  def mainProcessing: Receive = commonProcessing orElse clusterProcessing orElse
    shareProcessing orElse pubSubProcessing orElse {

    case InitialShareTimeout => // Ignore
    case msg => log.warning("Unknown message type received: {}", msg)
  }

  /**
   * Process the pub/sub messages messages
   * @return
   */
  def pubSubProcessing: Receive = {
    case message: Subscribe => subscribe(message)
    case message: Unsubscribe => unsubscribe(message)
    case message: Publish => forwardToTopic(message)
    case message: Send => forwardToTopic(message)
  }

  /**
   * Process the message sharing messages
   * @return
   */
  def shareProcessing: Receive = {
    case Share => shareSubscriptions
    case Status(remoteVersions) => verifyVersions(remoteVersions, sender.path)
    case Delta(entries) => updateDeltas(entries)
  }

  /**
   * Process cluster specific messages
   * @return
   */
  def clusterProcessing: Receive = {
    case CurrentClusterState(members, _, _, _, _) => updateNodeStatus(members.map {
      case m => (m.address, m.status)
    }.toSeq)
    case UnreachableMember(member) => updateNodeStatus(Seq((member.address, Unreachable)))
    case ReachableMember(member) => updateNodeStatus(Seq((member.address, Reachable)))
    case m: MemberEvent => updateNodeStatus(Seq((m.member.address, m.member.status)))
    case Terminated(ref) => terminated(ref)
  }

  /**
   * Process messages that are required either during initialization or actual running phases
   * @return
   */
  def commonProcessing: Receive = {
    // ---- Subscription Event Registration ----
    case RegisterSubscriptionEvent(registrar, to) => eventStream.subscribe(registrar, to)
    case UnregisterSubscriptionEvent(registrar, to) => eventStream.unsubscribe(registrar, to)

    case GetSubscriptions(topics) =>
      var map: Map[String, Seq[ActorSelection]] = Map.empty

      topics foreach {
        topic =>
          val set = (for {
            entry <- registry.values
            if (entry.content.contains(topic))
            sub <- entry.content(topic).subscriptions
          } yield context.actorSelection(sub.subscriber.path.toStringWithAddress(entry.address))).toSeq

          map += (topic -> set)
      }
      sender() ! map

    case CheckHealth =>
      import context.dispatcher
      log.debug("MessageProcessor health requested")
      pipe(Future {
        HealthComponent("processor", ComponentState.NORMAL, s"The message processor is currently running and managing ${context.children.size} topics")
      }) to sender()
  }

  /**
   * Add the subscription
   * @param message the Subscribe message
   */
  private def subscribe(message: Subscribe): Unit = {
    val lr = registry(selfAddress)
    val v = lr.clock.counter + 1
    val currentTime = System.currentTimeMillis
    val newClock = VectorClock(v, currentTime)
    val sub = Subscription(message.ref, message.localOnly)

    message.topics foreach {
      topic =>
        val newLr = registry(selfAddress).copy(
          address = selfAddress,
          clock = lr.clock.copy(counter = v, time = currentTime),
          availableRemote = true,
          content = lr.content.get(topic).map {
            topicEntry =>
              val newEntry = TopicEntry(
                topic,
                newClock,
                topicEntry.subscriptions ++ List(sub))
              lr.content.updated(topic, newEntry)
          }.getOrElse {
            lr.content + (topic -> TopicEntry(topic, newClock, Set(sub)))
          }
        )

        registry += (selfAddress -> newLr)

        log.info("The actor [{}] is subscribing to the topic {}", message.ref.path, topic)
        // Make sure we have a topic actor
        val encode = URLEncoder.encode(topic, "utf-8")
        context.child(encode) match {
          case Some(childRef) => childRef forward message
          case None =>
          // Do nothing since the topic actor does not currently exist and it will be created
          // whenever an actual message is send to it
        }

        publishSubscriptionEvent(true, topic, message.ref)
        sender() ! SubscribeAck(message)
    }

    context watch message.ref
  }

  /**
   * Remove the subscription
   * @param message the Unsubscribe message
   */
  private def unsubscribe(message: Unsubscribe): Unit = {
    val v = registry(selfAddress).clock.counter + 1
    val currentTime = System.currentTimeMillis

    message.topics foreach {
      topic =>
        val lr = registry(selfAddress)
        // Update the registry
        lr.content.get(topic).map {
          topicEntry =>
            val newEntry = (selfAddress -> lr.copy(
              clock = lr.clock.copy(counter = v, time = currentTime),
              content = lr.content.updated(topic, topicEntry.copy(
                topic = topic,
                clock = lr.clock.copy(counter = v, time = currentTime),
                subscriptions = topicEntry.subscriptions.filterNot(_.subscriber == message.ref)))
            ))

            // Now check to see if we have any remaining subscriptions for this topic. If not, then we
            // can remove the topic all together
            if (newEntry._2.content(topic).subscriptions.isEmpty) {
              registry += (selfAddress -> newEntry._2.copy(
                content = newEntry._2.content - topic))
            }
            else {
              registry += newEntry
            }

        }.getOrElse(log.warning("Received unsubscribe from topic {} which was not subscribed to", topic))

        log.info("The actor [{}] is unsubscribing to the topic {}", message.ref.path, topic)

        // Send a message to the topic actor to remove the subscription
        context.child(URLEncoder.encode(topic, "utf-8")) match {
          case Some(g) => g forward message
          case None =>
          // Do nothing since the topic actor does not currently exist and it will be created
          // whenever an actual message is send to it
        }

        publishSubscriptionEvent(false, topic, message.ref)
        sender() ! UnsubscribeAck(message)
    }

    // If we have no more subscriptions for this actor then we should unwatch it
    val refs = (for {
      entries <- registry.values
      subs <- entries.content.collect {
        case m => m._2.subscriptions
      }
      sub <- subs
      if (sub.subscriber.equals(message.ref))
    } yield sub).toSet

    if (refs.isEmpty) {
      context unwatch message.ref
    }
  }

  /**
   * An actor we have been watching has been terminated. We will now
   * update our internal registry for subscriptions that are related
   * to the given actor
   * @param actorRef the actor that has terminated
   */
  private def terminated(actorRef: ActorRef): Unit = {

    // Get all of the topics for this actor
    val topics = (for {
      content <- registry(selfAddress).content
      sub <- content._2.subscriptions
      if (sub.subscriber.equals(actorRef))
    } yield content._1).toSeq.distinct

    log.info("The actor [{}] has been terminated and it's subscriptions will be removed", actorRef.path)
    unsubscribe(Unsubscribe(topics, actorRef))
  }

  /**
   * Forward the given message to the actor for the
   * given topic.
   * @param message the message to forward
   */
  private def forwardToTopic(message: MessageCommand): Unit = {
    val encode = URLEncoder.encode(message.topic, "utf-8")

    context.child(encode) match {
      case Some(g) => g forward message
      case None =>
        // If there is no topic actor then we need to "seed" it and then forward the message
        val refs = (for {
          entries <- registry.values
          subs <- entries.content.collect {
            case m if m._1.equals(message.topic) => m._2.subscriptions
          }
          if (!subs.isEmpty)
          sub <- subs
        } yield sub).toSet

        // TODO - What if no subscribers
        if (!refs.isEmpty) {
          val childRef = context.actorOf(MessagingTopicActor.props(selfAddress, trashInterval, refs), name = encode)
          childRef forward message
        }
        else {
          log.warning("The message to the topic {} could not be pushed because there were no subscribers", message.topic)
        }
    }
  }

  /**
   * Share the subscription information with peer nodes.
   */
  private def shareSubscriptions: Unit = {
    randomNode match {
      case Some(node) =>
        log.debug("Sharing subscription information with {}", node)
        shareWith(node) ! Status(versions = localVersions)
      case None =>
        log.debug("No nodes to share subscription information")
    }
  }

  /**
   * Get the reference to a remote peer to share state with
   * @param address the address of the remote peer
   * @return an instance of ActorSelection
   */
  private def shareWith(address: Address): ActorSelection = context.actorSelection(self.path.toStringWithAddress(address))

  /**
   * Select a random cluster node to share our subscription information
   * with.
   * @return the address of a random remote peer
   */
  private def randomNode: Option[Address] = {
    val otherNodes = nodes.filter(add => add != selfAddress && registry(add).availableRemote).toIndexedSeq
    if (otherNodes.isEmpty) {
      None
    } else {
      val randomIndex = ThreadLocalRandom.current.nextInt(otherNodes.length)
      Some(otherNodes(randomIndex))
    }
  }

  /**
   * Determine if the local state is newer then the remote state and then
   * act accordingly. If there are deltas then send those back to the caller.
   * @param remoteVersions the state from a remote peer
   * @param sourcePath the Path for the remote actor that is sharing this information
   */
  private def verifyVersions(remoteVersions: Map[Address, Long], sourcePath: ActorPath): Unit = {

    if (!nodes(sender().path.address)) {
      log.info("Ignoring received subscription information status from unknown node [{}] ", sender().path)
    }
    else {
      log.debug(s"Verifying versions from {}", sourcePath)

      // See if our data is "newer" then the remote service
      val delta = collectDelta(remoteVersions)
      val newer = remoteHasNewerVersion(remoteVersions)
      if (delta.isEmpty && !newer) {
        if (shareTask.isEmpty) {
          self ! InitialShareComplete
        }
      }
      else {
        if (delta.nonEmpty) {
          log.debug("The subscription information for {} is newer then {} so we are sending the delta back", self.path, sourcePath)
          sender() ! Delta(delta)
        }
        // Now check and see if any of the remote data is newer then ours
        if (newer) {
          log.debug("The subscription information for {} is newer then {} so we are asking for the delta back", sourcePath, self.path)
          sender() ! Status(versions = localVersions) // it will reply with Delta
        }
      }
    }
  }

  /**
   * Determine the deltas between the local state and one from a remote peer
   * @param remoteVersions the state from a remote peer
   * @return the deltas
   */
  private def collectDelta(remoteVersions: Map[Address, Long]): immutable.Iterable[RegistryEntry] = {
    // Combines the local and remote versions. If the local version does not exist then it
    // will be represented by a version of 0.
    val local = localVersions

    // Get the newer local nodes
    local.collect {
      // If the remote entry is missing or if our local version is newer then pull it out here
      case (add, v) if !remoteVersions.contains(add) || v > remoteVersions(add) =>
        val entry = registry(add)
        val remote = remoteVersions.get(add)
        if (remote.isDefined) {
          log.debug(remoteVersions.mkString(","))
          log.debug(s"Version {} for {} is older then the local version of {}", remote.get, add, v)
        }
        else {
          log.debug(s"The registry for {} is not present in the gossiped subscriptions. Adding it to the deltas.", add)
        }

        // Grab the non-local subscriptions for this node
        val nonLocal = entry.content.filter {
          case (_, value) => remote.isEmpty || value.clock.counter > remote.get
        } map {
          d =>
            d._1 -> d._2.copy(subscriptions = d._2.subscriptions.filter(_.localOnly == false))
        }
        registry(add).copy(content = nonLocal)

    }
  }

  /**
   * Update our internal state of subscriptions with the remote deltas
   * @param deltas The deltas
   */
  private def updateDeltas(deltas: immutable.Iterable[RegistryEntry]): Unit = {
    // Reply from Status message in the gossip chat
    // the Delta contains potential updates (newer versions) from the other node

    // Only accept deltas/buckets from known nodes, otherwise there is a risk of
    // adding back entries when nodes are removed
    if (nodes(sender().path.address)) {

      // Don't update local registrations
      deltas.filterNot(_.address == selfAddress) foreach {
        b =>
          if (nodes(b.address)) {
            val entry = registry(b.address)
            if (b.clock.counter > entry.clock.counter) {

              // Diff the existing subscriptions with the new version.
              val removals = (for {
                kp <- entry.content
                sub <- kp._2.subscriptions
                // If the topic is not in the new set or if the specific actor path is not then we know that is has been removed
                if (!b.content.contains(kp._1) || b.content(kp._1).subscriptions.filter(_.subscriber.path == sub.subscriber.path).isEmpty)
              } yield (kp._2.topic, sub.subscriber)).toSet

              val adds = (for {
                kp <- b.content
                sub <- kp._2.subscriptions
                // If the topic is not in the old set or if the specific actor path is not then we know that is has been added
                if (!entry.content.contains(kp._1) || entry.content(kp._1).subscriptions.filter(_.subscriber.path == sub.subscriber.path).isEmpty)
              } yield (kp._2.topic, sub.subscriber)).toSet

              // Merge the existing subscriptions with the deltas
              val newEntry = entry.copy(
                clock = entry.clock.copy(counter = b.clock.counter, time = b.clock.time ),
                content = entry.content ++ b.content
              )
              // Now update the registry
              registry += (b.address -> newEntry)

              // These were removed
              removals foreach {
                r => publishSubscriptionEvent(false, r._1, r._2)
              }
              // These were added
              adds foreach {
                r => publishSubscriptionEvent(true, r._1, r._2)
              }

              log.debug("There is a difference in subscription data so the system will update using the deltas sent to it from {}: {} [remote], {} [local]",
                b.address, b.clock, entry.clock)

              // Now we need to update the subscriptions for any existing topic
              updateTopicSubscriptions
            }
          }
      }

      // The sharing process has completed
      if (shareTask.isEmpty) self ! InitialShareComplete
    }
  }

  /**
   * Update the child topic actors with the local copy of subscriptions
   */
  private def updateTopicSubscriptions: Unit = {
    context.children foreach {
      child =>
        val topic = URLDecoder.decode(child.path.name, "utf-8")

        val refs = (for {
          entry <- registry.values
          if (entry.availableRemote) // Only allow subscriptions for nodes that are currently available
          subs <- entry.content.collect {
            case m if m._1.equals(topic) => m._2.subscriptions
          }
          if (!subs.isEmpty)
          sub <- subs
        } yield sub).toSet

        log.debug("Sending the updated the subscriptions for the topic {}: {}", topic, refs.map(_.subscriber.path).mkString(","))
        child ! UpdateSubscriptions(refs)
    }
  }

  /**
   * Update the availability of subscriptions for a given node
   * @param members a sequence of tuples with Address and MemberStatus.
   */
  private def updateNodeStatus(members: Seq[(Address, MemberStatus)]): Unit = {
    members foreach {
      member =>

        def updateNodeRemoteStatus(entry: RegistryEntry, available: Boolean) = {
          if (entry != null) {
            // Update the status of the given node
            registry += (member._1 -> entry.copy(availableRemote = available))
          }
        }

        member._2 match {
          case MemberStatus.Joining => // Do nothing

          case MemberStatus.Up =>
            // Add the member to the list of nodes
            nodes += member._1
            updateNodeRemoteStatus(registry(member._1), true)

          case Unreachable =>
            // We will just update the node status so that no messages are sent to those locations, but the subscriptions are retained until the
            // node is actually removed
            updateNodeRemoteStatus(registry(member._1), false)

          case Reachable =>
            // The node has become reachable again so reinstate it.
            updateNodeRemoteStatus(registry(member._1), true)

          case _ => // All other status are either from the node being removed or on it's way out the door
            // Remove the member from the list of nodes and registry
            removeNode(member)
        }
    }

    // Now we need to update the subscriptions for any existing topic
    updateTopicSubscriptions
  }

  /**
   * Remove the member from the list of nodes and registry
   * @param member
   */
  private def removeNode(member: (Address, MemberStatus)): Unit = {
    if (member._1 == selfAddress) {
      // If we are asked to be removed then just shut ourself down
      context stop self
    }
    else {
      nodes -= member._1
      // Notify listeners of the subscription removals
      registry(member._1).content.values foreach {
        c => c.subscriptions foreach {
          s => publishSubscriptionEvent(false, c.topic, s.subscriber)
        }
      }
      registry -= member._1
      log.info("The node [{}] is no longer available [{}] and it's subscriptions will be removed", member._1, member._2)
    }
  }

  /**
   * Publish the subscription command for all listening on the pre-defined topic
   * {@link com.webtrends.portfolio.communication.MessageService.MessageSubscriptionEventTopic}.
   * @param topic the topic that changed
   * @param subscriber the actor ref that is involved
   */
  private def publishSubscriptionEvent(added: Boolean, topic: String, subscriber: ActorRef): Unit = {
    if (added) {
      log.debug(s"Publishing a subscription add event for the topic ${topic}")
      eventStream publish SubscriptionAddedEvent(topic, subscriber)
    }
    else {
      log.debug(s"Publishing a subscription remove event for the topic ${topic}")
      eventStream publish SubscriptionRemovedEvent(topic, subscriber)
    }
  }

  /**
   * Are the versions for the given remote addresses newer then the local version
   * @param remoteVersions the map of addresses and versions
   * @return true if the any of the remote versions are newer then the local copy
   */
  private def remoteHasNewerVersion(remoteVersions: Map[Address, Long]): Boolean =
    remoteVersions.exists {
      case (address, version) => version > registry(address).clock.counter
    }
}