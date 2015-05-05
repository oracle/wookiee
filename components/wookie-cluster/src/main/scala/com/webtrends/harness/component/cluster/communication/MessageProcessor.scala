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

import akka.actor.{ActorRef, Address}
import scala.collection.immutable

/*
 * @author cuthbertm on 10/22/14 9:19 AM
 */
object MessageProcessor {

  // Class for requesting a copy of the subscriptions
  @SerialVersionUID(1L) case class GetSubscriptionsMap()
  @SerialVersionUID(1L) case class MessagingStarted()

  private[harness] object Internal {

    // Classes for subscription registry
    @SerialVersionUID(1L) case class Subscription(subscriber: ActorRef, localOnly: Boolean) {
      override def equals( arg: Any ) : Boolean = arg match {
        case Subscription(s, _) => s == subscriber
        case _ => false
      }
      override def hashCode() = subscriber.hashCode()
    }

    @SerialVersionUID(1L) case class VectorClock(counter: Long, time: Long)
    @SerialVersionUID(1L) case class UpdateSubscriptions(subscriptions: Set[Subscription])
    @SerialVersionUID(1L) case class RegistryEntry(address: Address, clock: VectorClock, availableRemote: Boolean, content: Map[String, TopicEntry])
    @SerialVersionUID(1L) case class TopicEntry(topic: String, clock: VectorClock, subscriptions: Set[Subscription])

    // Classes for sharing subscriptions
    @SerialVersionUID(1L) case class Status(versions: Map[Address, Long])
    @SerialVersionUID(1L) case class Delta(entries: immutable.Iterable[RegistryEntry])
  }
}
