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

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem}
import akka.event.Logging
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.component.cluster.communication.MessageService._
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.Internal.{UnregisterSubscriptionEvent, RegisterSubscriptionEvent}
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.MessageSubscriptionEvent

import scala.concurrent.Future

/**
 * @author cuthbertm on 10/22/14 10:26 AM
 */
private[communication] class MessageService(mediator: ActorRef)(implicit system: ActorSystem) {

  private[communication] val defaultTimeout = Timeout(system.settings.config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS))
  private val log = Logging(system, this.getClass)

  /**
   * Subscribe for messages. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic to receive message from
   * @param subscriber the actor that is to receive the messages. If this actor is terminated then
   *                   the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   * @param sender the ActorRef whom called this method and acknowledgement will be sent to.
   */
  def subscribe(topic: String, subscriber: ActorRef, localOnly: Boolean)
               (implicit sender: ActorRef): Unit = {
    mediator ! Subscribe(Seq(topic), subscriber, localOnly)
  }

  /**
   * Subscribe for messages. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topics the topics to receive message from
   * @param subscriber the actor that is to receive the messages. If this actor is terminated then
   *                   the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   * @param sender the ActorRef whom called this method and acknowledgement will be sent to.
   */
  def subscribe(topics: Seq[String], subscriber: ActorRef, localOnly: Boolean)
               (implicit sender: ActorRef): Unit = {
    mediator ! Subscribe(topics, subscriber, localOnly)
  }

  /**
   * Un-subscribe to the given topic
   * @param topic the topic to un-subscribe
   * @param subscriber the actor to un-subscribe
   * @param sender the ActorRef whom called this method and acknowledgement will be sent to.
   */
  def unsubscribe(topic: String, subscriber: ActorRef)
                 (implicit sender: ActorRef): Unit = {
    mediator ! Unsubscribe(Seq(topic), subscriber)
  }

  /**
   * Un-subscribe to the given topic
   * @param topics the topics to un-subscribe
   * @param subscriber the actor to un-subscribe
   * @param sender the ActorRef whom called this method and acknowledgement will be sent to.
   */
  def unsubscribe(topics: Seq[String], subscriber: ActorRef)
                 (implicit sender: ActorRef): Unit = {
    mediator ! Unsubscribe(topics, subscriber)
  }

  /**
   * Get the subscribers for the given topics. This method should normally only be used as a reference point.
   * @param topics the topics to look up subscribers
   * @return a future that contains a map of topics to subscribers
   */
  def getSubscriptions(topics: Seq[String])(implicit timeout: akka.util.Timeout = defaultTimeout): Future[Map[String, Seq[ActorSelection]]] =
    (mediator ? GetSubscriptions(topics)).mapTo[Map[String, Seq[ActorSelection]]]

  /**
   * Sends the message to the given topic. The message will go
   * to one subscriber and is a one-way asynchronous message. E.g. fire-and-forget semantics.
   * When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   * @param sender the ActorRef to act as the sender.
   */
  def send(topic: String, msg: Any)
          (implicit sender: ActorRef): Unit = {
    (mediator ! Send(topic, Message.createMessage(topic, msg)))(sender)
  }

  /**
   * Sends the message to the given topic. The message will go
   * to one subscriber and a future will be returned.
   * When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   * @param timeout the implicit timeout
   */
  def sendWithFuture(topic: String, msg: Any)(implicit timeout: akka.util.Timeout = defaultTimeout): Future[Any] = {
    mediator ? Send(topic, Message.createMessage(topic, msg))
  }

  /**
   * Publish the message to the given topic. The message will go
   * to all subscribers and no return is made. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   */
  def publish(topic: String, msg: Any): Unit = {
    mediator ! Publish(topic, Message.createMessage(topic, msg))
  }

  /**
   * Register for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def register(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit =
    mediator ! RegisterSubscriptionEvent(registrar, to)

  /**
   * Unregister for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def unregister(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit =
    mediator ! UnregisterSubscriptionEvent(registrar, to)
}

object MessageService {

  def apply()(implicit system: ActorSystem): MessageService = new MessageService(mediator.get)

  private var mediator: Option[ActorRef] = None

  def registerMediator(actor: ActorRef) = {
    mediator = Some(actor)
  }

  def unregisterMediator = {
    mediator = None
  }

  def unregisterMediator(actor: ActorRef) = {
    mediator match {
      case Some(m) if m == actor => mediator = None
      case _ => //just do nothing
    }
  }

  sealed trait MessageCommand {
    def topic: String
  }

  sealed trait MessageSubscriptionCommand {
    def topics: Seq[String]
  }

  /**
   * This is a pre-defined topic that is used to broadcast subscription events.
   * This is not used for maintaining subscriptions, but can be used more for testing
   * subscription events. The message received by the subscriber will contain
   * an instance of {@link com.webtrends.harness.logging.communication.MessageService.Subscribe}
   * or {@link com.webtrends.harness.logging.communication.MessageService.Unsubscribe}.
   */
  val MessageSubscriptionEventTopic = "message-subscription-event"

  /**
   * Subscribe to the given topic
   * @param topics the topics to receive message from
   * @param ref the actor that is to receive the messages. If this actor is terminated then
   *            the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   */
  @SerialVersionUID(1L) case class Subscribe(val topics: Seq[String], ref: ActorRef, localOnly: Boolean) extends MessageSubscriptionCommand

  /**
   * Acknowledgement of the subscription
   * @param subscribe the original Subscribe message
   */
  @SerialVersionUID(1L) case class SubscribeAck(subscribe: Subscribe)

  /**
   * Un-subscribe to the given topic
   * @param topics the topics to un-subscribe
   * @param ref the actor to un-subscribe
   */
  @SerialVersionUID(1L) case class Unsubscribe(val topics: Seq[String], ref: ActorRef) extends MessageSubscriptionCommand

  /**
   * Acknowledgement of the un-subscription
   * @param unsubscribe the original Unsubscribe message
   */
  @SerialVersionUID(1L) case class UnsubscribeAck(unsubscribe: Unsubscribe)

  /**
   * Get a map of subscribers for the given topic. This method should normally only be used as a reference point.
   * @param topics the topics to look up subscribers
   */
  @SerialVersionUID(1L) case class GetSubscriptions(val topics: Seq[String]) extends MessageSubscriptionCommand

  /**
   * Publish a message to the given topic
   * @param topic the topic to send the message to
   * @param msg the message to send
   */
  @SerialVersionUID(1L) case class Publish(val topic: String, msg: Message) extends MessageCommand

  /**
   * Send a message to the given topic. The message will only be sent to one
   * of the subscribers for the topic.
   * @param topic the topic to send the message to
   * @param msg the message to send
   */
  @SerialVersionUID(1L) case class Send(val topic: String, msg: Message) extends MessageCommand

}

trait MessagingAdapter {
  this: Actor =>

  import context.system

  private lazy val messageService = MessageService()

  /**
   * Subscribe for messages. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * A SubscribeAck message will be sent to the caller of this method to acknowledge the
   * addition of the subscription.
   * @param topic the topic to receive message from
   * @param subscriber the actor that is to receive the messages. If this actor is terminated then
   *                   the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   */
  def subscribe(topic: String, subscriber: ActorRef = self, localOnly: Boolean = false): Unit = {
    messageService.subscribe(topic, subscriber, localOnly)(self)
  }

  /**
   * Subscribe for messages. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * A SubscribeAck message will be sent to the caller of this method to acknowledge the
   * addition of the subscription.
   * @param topics the topics to receive message from
   * @param subscriber the actor that is to receive the messages. If this actor is terminated then
   *                   the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   */
  def subscribeToMany(topics: Seq[String], subscriber: ActorRef = self, localOnly: Boolean = false): Unit = {
    messageService.subscribe(topics, subscriber, localOnly)(self)
  }

  /**
   * Un-subscribe to the given topic.
   * An UnsubscribeAck message will be sent to the caller of this method to acknowledge the
   * addition of the subscription.
   * @param topic the topic to un-subscribe
   * @param subscriber the actor to un-subscribe
   */
  def unsubscribe(topic: String, subscriber: ActorRef = self): Unit = {
    messageService.unsubscribe(topic, subscriber)(self)
  }

  /**
   * Un-subscribe to the given topic.
   * An UnsubscribeAck message will be sent to the caller of this method to acknowledge the
   * addition of the subscription.
   * @param topics the topic to un-subscribe
   * @param subscriber the actor to un-subscribe
   */
  def unsubscribeFromMany(topics: Seq[String], subscriber: ActorRef = self): Unit = {
    messageService.unsubscribe(topics, subscriber)(self)
  }

  /**
   * Get the subscribers for the given topics. This method should normally only be used as a reference point.
   * @param topics the topics to look up subscribers
   * @return a future that contains a map of topics to subscribers
   */
  def getSubscriptions(topics: Seq[String]): Future[Map[String, Seq[ActorSelection]]] = messageService.getSubscriptions(topics)

  /**
   * Sends the message to the given topic. The message will go
   * to one subscriber and is a one-way asynchronous message. E.g. fire-and-forget semantics.
   * When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   * @param sender the implicit ActorRef to act as the sender and will default to self
   */
  def send(topic: String, msg: Any)(implicit sender: ActorRef = context.self): Unit = {
    messageService.send(topic, msg)(sender)
  }

  /**
   * Sends the message to the given topic. The message will go
   * to one subscriber and a future will be returned.
   * When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   * @param timeout the implicit timeout
   */
  def sendWithFuture(topic: String, msg: Any)(implicit timeout: akka.util.Timeout): Future[Any] = {
    messageService.sendWithFuture(topic, msg)(timeout)
  }

  /**
   * Publish the message to the given topic. The message will go
   * to all subscribers and no return is made. When receiving a message, it will be wrapped in an instance
   * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
   * @param topic the topic for the message
   * @param msg the message to send
   */
  def publish(topic: String, msg: Any): Unit = {
    messageService.publish(topic, msg)
  }
}
