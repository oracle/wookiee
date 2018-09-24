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
package com.webtrends.harness.health

import java.util.concurrent.TimeUnit

import akka.actor.ActorDSL._
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.app.HActor
import com.webtrends.harness.service.messages.CheckHealth
import org.specs2.mutable.SpecificationWithJUnit
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

class HealthCheckActorSpec extends SpecificationWithJUnit {

  implicit val dur = FiniteDuration(15, TimeUnit.SECONDS)

  implicit val sys = ActorSystem("system", ConfigFactory.parseString( """
    akka.actor.provider = "akka.actor.LocalActorRefProvider"
                                                                      """).withFallback(ConfigFactory.load))
  implicit val ec: ExecutionContextExecutor =  sys.dispatcher

  step {
    val sysActor =
      actor("system")(new Act {
        become {
          case CheckHealth => sender() ! Seq(HealthComponent("test", ComponentState.NORMAL, "test"))
        }
      })
  }

  "The health check actor" should {

    "Return system Health when asking for health information" in {

      val probe = new TestProbe(sys)
      val actor = TestActorRef(HealthCheckActor.props)

      probe.send(actor, HealthRequest(HealthResponseType.FULL))
      val msg = probe.expectMsgClass(classOf[ApplicationHealth])
      msg.applicationName equalsIgnoreCase "Webtrends Harness Service"
    }

    "Time out with correct error when child has no health check" in {
      val actor = sys.actorOf(Props(new TopActor()), "top")

      val result = Await.result[HealthComponent](actor.ask(CheckHealth)(Timeout(FiniteDuration(15, TimeUnit.SECONDS)))
        .mapTo[HealthComponent], FiniteDuration(15, TimeUnit.SECONDS))
      result.components.head.state mustEqual ComponentState.CRITICAL
    }
  }

  step {
    sys.terminate().onComplete(_ => {})
  }

  class TopActor() extends HActor {
    override implicit val checkTimeout = Timeout(2 seconds)
    val lActor = context.actorOf(Props(new LowerActor()), "lower")
  }

  class LowerActor() extends Actor { // Not a health actor, so won't respond to CheckHealth
    override def receive = {
      case _ =>
    }: Receive
  }
}
