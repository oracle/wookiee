/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.websocket

import java.util.concurrent.LinkedBlockingDeque

import akka.actor._
import akka.dispatch._
import com.typesafe.config.Config

/**
  * Specialist priority (user provides the rules), unbounded, deque
  * (can be used for Stashing) mailbox.
  *
  * Very useful for messages of high priority, such as `Ack`s in I/O
  * situations.
  *
  * Based on UnboundedDequeBasedMailbox from Akka.
  */
abstract class PriorityUnboundedDequeMailbox
    extends MailboxType
    with ProducesMessageQueue[UnboundedDequeBasedMailbox.MessageQueue] {
  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new PriorityUnboundedDequeMailbox.MessageQueue(priority, isDupe)

  /**
    * When true, the queue will place this envelope at the front of the
    * queue (as if it was just stashed).
    */
  def priority(e: Envelope): Boolean

  /**
    * When true, the new envelope will replace the last envelope in the queue
    * When false, it will be added to the end
    */
  def isDupe(lastEnv: Envelope, newEnv: Envelope): Boolean
}

object PriorityUnboundedDequeMailbox {

  class MessageQueue(priority: Envelope => Boolean, isDupe: (Envelope, Envelope) => Boolean)
      extends LinkedBlockingDeque[Envelope]
      with UnboundedDequeBasedMessageQueue {
    def queue: MessageQueue = this

    override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
      if (priority(handle)) {
        super.enqueueFirst(receiver, handle)
      } else if (size > 0 && isDupe(handle, queue.peekLast())) {
        super.removeLast()
        super.enqueue(receiver, handle)
      } else {
        super.enqueue(receiver, handle)
      }
  }
}
