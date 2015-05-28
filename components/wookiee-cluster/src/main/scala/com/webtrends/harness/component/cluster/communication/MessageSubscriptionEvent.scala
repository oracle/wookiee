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

import akka.actor.{Actor, ActorRef}
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.MessageSubscriptionEvent

/*
 * @author vonnagyi on 8/30/13 11:25 AM
 */
object MessageSubscriptionEvent {

  /**
   * Marker interface for message domain events.
   */
  sealed trait MessageSubscriptionEvent {
    def topic: String

    def ref: ActorRef
  }

  /**
   * Subscription registration has been added. Published when a change has occured
   * in the subscription information. This occurs from either internal subscribe/unsubscribe
   * events or when an external system has changed.
   */
  @SerialVersionUID(1L) case class SubscriptionAddedEvent(val topic: String, ref: ActorRef) extends MessageSubscriptionEvent

  /**
   * Subscription registration has been remove. Published when a change has occured
   * in the subscription information. This occurs from either internal subscribe/unsubscribe
   * events or when an external system has changed.
   */
  @SerialVersionUID(1L) case class SubscriptionRemovedEvent(val topic: String, ref: ActorRef) extends MessageSubscriptionEvent

  private[harness] object Internal {

    @SerialVersionUID(1L) case class RegisterSubscriptionEvent(registrar: ActorRef, to: Class[_])

    @SerialVersionUID(1L) case class UnregisterSubscriptionEvent(registrar: ActorRef, to: Class[_])

  }

}

trait MessageSubscriptionEventAdapter {
  this: Actor =>

  import context.system

  lazy val msgEventService = MessageService()

  /**
   * Register for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def register(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit = msgEventService.register(registrar, to)

  /**
   * Unregister for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def unregister(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit = msgEventService.unregister(registrar, to)
}
