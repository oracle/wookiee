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

package com.oracle.infy.wookiee.component.akkahttp.util

import akka.actor.{Actor, ActorSystem}
import akka.dispatch.Envelope
import akka.testkit.TestKit
import com.oracle.infy.wookiee.component.akkahttp.websocket.PriorityUnboundedDequeMailbox
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class PriorityUnboundedDequeMailboxSpec
    extends TestKit(ActorSystem("PriorityUnboundedDequeMailbox"))
    with AnyWordSpecLike
    with Matchers {
  "PriorityUnboundedDequeMailbox " should {
    "prevent consecutive items" in {

      val queue = new PriorityUnboundedDequeMailbox(system.settings, null) {
        override def priority(e: Envelope) = false
        override def isDupe(lastEnv: Envelope, newEnv: Envelope): Boolean = {
          val lEnv = lastEnv.message.asInstanceOf[(String, Dupe)]
          val nEnv = newEnv.message.asInstanceOf[(String, Dupe)]
          lEnv._2 == nEnv._2
        }
      }.create(None, None)

      trait Dupe
      case object DupeId1 extends Dupe
      case object DupeId2 extends Dupe
      case object DupeId3 extends Dupe

      queue.enqueue(null, Envelope(("1", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("2", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("3", DupeId3), Actor.noSender, system))
      queue.enqueue(null, Envelope(("4", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("5", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("6", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("7", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("8", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("9", DupeId2), Actor.noSender, system))

      queue.numberOfMessages mustEqual 6
      val values =
        for (n <- 0 until queue.numberOfMessages) yield queue.dequeue().message.asInstanceOf[(String, Dupe)]._1
      values mustEqual Seq("1", "2", "3", "4", "7", "9")
      queue.numberOfMessages mustEqual 0
    }
  }
}
