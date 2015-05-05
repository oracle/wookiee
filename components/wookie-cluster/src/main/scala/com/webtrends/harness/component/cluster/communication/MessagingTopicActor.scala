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
/**
 * @author cuthbertm on 10/22/14 9:23 AM
 */
package com.webtrends.harness.component.cluster.communication

import java.net.URLDecoder

import akka.actor._
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import com.webtrends.harness.component.cluster.communication.MessageProcessor.Internal.{UpdateSubscriptions, Subscription}
import com.webtrends.harness.component.cluster.communication.MessageService.{Publish, Unsubscribe, Send, Subscribe}
import com.webtrends.harness.logging.ActorLoggingAdapter

import scala.concurrent.duration.{Deadline, FiniteDuration}

object MessagingTopicActor {
  def props(selfAddress: Address, trashInterval: FiniteDuration, seed: Set[Subscription]): Props = {
    Props(classOf[MessagingTopicActor], selfAddress, trashInterval, seed)
  }
}

class MessagingTopicActor(selfAddress: Address, trashInterval: FiniteDuration, seed: Set[Subscription]) extends Actor
with ActorLoggingAdapter {

  import context.dispatcher

  // The name of this actor is actually the topic
  val topic = URLDecoder.decode(self.path.name, "utf-8")

  // Setup a recurring task that checks to see if we have no more subscribers.
  // This helps to prevent the retention of resources that are not needed.
  case object Sweep

  val sweepInterval: FiniteDuration = trashInterval / 2
  val sweepTask = context.system.scheduler.schedule(sweepInterval, sweepInterval, self, Sweep)
  var trashDeadline: Option[Deadline] = None

  // The list of subscribers
  var registry = Map.empty[ActorRef, Boolean]

  // Establish the routing logic
  val routingLogic = RoundRobinRoutingLogic()

  override def preStart: Unit = {
    // Take the seed subscriptions and set them up
    updateSubscriptions(seed)
  }

  override def postStop(): Unit = {
    sweepTask.cancel()
    super.postStop()
  }

  def receive = {
    case UpdateSubscriptions(subscriptions) => updateSubscriptions(subscriptions)
    case message@Subscribe(_, ref, localOnly) => subscribe(ref, localOnly)
    case message@Unsubscribe(_, ref) => unsubscribe(ref, false)
    case Terminated(ref) => unsubscribe(ref, true)
    case Publish(_, message) => publish(message)
    case Send(_, message) => send(message)
    case Sweep if trashDeadline.isDefined && trashDeadline.get.isOverdue =>
      log.debug("The actor [{}] is stopping because there are no more subscriptions and it was scheduled for deletion", self.path)
      context stop self

  }

  /**
   * Bulk update the subscriptions
   * @param subscriptions
   */
  private def updateSubscriptions(subscriptions: Set[Subscription]): Unit = {
    log.info("The actor [{}] is updating it's subscriptions: {}", self.path, subscriptions.map(_.subscriber.path).mkString(","))

    // Unwatch all of the entries that are not in the passed in set
    val removals = registry.map(_._1).toSet &~ subscriptions.map(_.subscriber)
    removals foreach { sub =>
      context unwatch sub
    }

    // Copy the subscriptions
    registry = subscriptions.map(e => (e.subscriber -> e.localOnly)).toMap
    if (registry.isEmpty) {
      // If we have no more subscriptions then schedule the removal of this actor
      trashDeadline = Some(Deadline.now + trashInterval)
    }
    else {
      trashDeadline = None
      registry foreach { sub =>
        context watch sub._1
      }
    }
  }

  /**
   * Add the subscription
   * @param ref the actor to subscribe
   * @param localOnly
   */
  private def subscribe(ref: ActorRef, localOnly: Boolean): Unit = {
    if (!registry.contains(ref)) {
      log.info("The actor {} is subscribing to the topic {}", ref.path.toString, topic)
      context watch ref
      registry += (ref -> localOnly)
      trashDeadline = None
    }
  }

  /**
   * Remove the subscription
   * @param ref the actor to unsubscribe
   * @param terminated is the unsubscribe due to a termination
   */
  private def unsubscribe(ref: ActorRef, terminated: Boolean): Unit = {
    if (registry.contains(ref)) {
      if (terminated) {
        log.info("The actor {} is un-subscribed to the topic {} since it has been terminated", ref.path.toString, topic)
      }
      else {
        log.info("The actor {} is un-subscribing to the topic {}", ref.path.toString, topic)
      }

      context unwatch ref
      registry -= ref
      if (registry.isEmpty) {
        // If we have no more subscriptions then schedule the removal of this actor
        trashDeadline = Some(Deadline.now + trashInterval)
      }
    }
  }

  /**
   * Forward the message to all subscribers
   * @param message
   */
  private def publish(message: Any): Unit = {
    for {
      subscription <- registry
      // Only send the message to internal actors or external ones that are registered for external publishes
      if (subscription._1.path.address.host.isEmpty || subscription._1.path.address == selfAddress || !subscription._2)
    } {
      log.debug("Publishing message for '{}' to {}", topic, subscription._1.path)
      subscription._1 forward message
    }
  }

  private def send(message: Any): Unit = {
    val routees = (for {
      sub <- registry
    } yield ActorRefRoutee(sub._1)).toVector

    if (routees.nonEmpty) {
      // Forward the message through our router
      log.debug("Routing the message")
      Router(routingLogic, routees).route(message, sender())
    }
    else {
      log.warn("There are no subscribers available for the topic {}", topic)
    }
  }
}
