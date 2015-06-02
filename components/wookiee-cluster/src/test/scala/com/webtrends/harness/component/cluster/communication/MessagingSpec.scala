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

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

@SerialVersionUID(1L) case class RequestMessage(probeRef: ActorRef)

@SerialVersionUID(1L) case class ResponseMessage(text: String)


class MessagingSpec extends TestKitSpecificationWithJUnit(ActorSystem("test",
  ConfigFactory.parseString( """
      akka.actor.provider = "akka.actor.LocalActorRefProvider"
    """).withFallback(ConfigFactory.load))) {

  implicit val timeout = Timeout(system.settings.config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS))
  val messageActor = system.actorOf(MessagingActor.props(system.settings.config), Messaging.MessagingName)

  lazy val msgActor1 =
    TestActorRef(new Act with MessagingAdapter {
      become {
        case m: Message => m.body.asInstanceOf[RequestMessage].probeRef ! ResponseMessage("foo")
      }
    }, "test1")

  sequential

  "The message service" should {
    val probe = new TestProbe(system)
    // Allow Messaging to start
    Thread.sleep(1000)
    "allow actors to subscribe and receive published messages" in {
      msgActor1.underlyingActor.subscribe("senditmyway", msgActor1, true)
      msgActor1.underlyingActor.publish("senditmyway", RequestMessage(probe.ref))
      ResponseMessage("foo") must be equalTo probe.expectMsg(ResponseMessage("foo"))
    }

    "allow actors to subscribe and receive sent messages" in {
      msgActor1.underlyingActor.subscribe("senditmyway", msgActor1, true)
      msgActor1.underlyingActor.send("senditmyway", RequestMessage(probe.ref))
      ResponseMessage("foo") must be equalTo probe.expectMsg(ResponseMessage("foo"))
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}
