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

package com.webtrends.harness

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.webtrends.harness.utils.ActorWaitHelper
import org.specs2.mutable.SpecificationLike

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WaitedOnActor extends Actor with ActorWaitHelper {
  def receive: Receive = {
    case "message" => sender ! "waitedResponse"
  }
}

class WaitActor extends Actor with ActorWaitHelper {
  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val waited = awaitActor(Props[WaitedOnActor])

  def receive: Receive = {
    case "message" => sender ! "response"
    case "waited" => sender ! Await.result((waited ? "message").mapTo[String], Duration(5, "seconds"))
  }
}

class ActorWaitSpec extends TestKit(ActorSystem("wait-spec")) with SpecificationLike {
  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val waitActor = ActorWaitHelper.awaitActor(Props[WaitActor], system)

  sequential

  "ActorWaitSpec" should {
    "await the WaitActor successfully " in {
      Await.result((waitActor ? "message").mapTo[String], Duration(5, "seconds")) must beEqualTo("response")
    }

    "the WaitActor's awaited actor must have come up " in {
      Await.result((waitActor ? "waited").mapTo[String], Duration(5, "seconds")) must beEqualTo("waitedResponse")
    }
  }

  step {
    waitActor ! PoisonPill
  }
}
