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
import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.service.messages.Ready
import com.webtrends.harness.service.test.TestSystemActor.RegisterShutdownListener
import com.webtrends.harness.service.test.{TestComponent, TestService, TestHarness}
import org.specs2.mutable.SpecificationWithJUnit

/**
 * woods
 * 12/31/14
 */
class TestHarnessSpec extends SpecificationWithJUnit {
  implicit val timeout = Timeout(2000, TimeUnit.MILLISECONDS)
  val sys = TestHarness(ConfigFactory.empty(), Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent])))
  implicit val actorSystem = TestHarness.system.get

  sequential

  "test harness " should {
    "start up service manager " in {
      sys.serviceManager must beSome[ActorRef]
    }

    "load test service " in {
      val probe = TestProbe()
      val testService = sys.getService("testservice")
      assert(testService.isDefined, "Test service was not registered")
      probe.send(testService.get, Ready)
      Ready must beEqualTo(probe.expectMsg(Ready))
    }

    "start up component manager " in {
      sys.componentManager must beSome[ActorRef]
    }

    "load test component " in {
      val probe = TestProbe()
      val testComponent = sys.getComponent("testcomponent")
      assert(testComponent.isDefined, "Test component was not registered")
      probe.send(testComponent.get, StatusRequest)
      TestComponent.ComponentMessage must beEqualTo(probe.expectMsg(TestComponent.ComponentMessage))
    }

    "shutdown services and components" in {
      val probe = TestProbe()
      val testService = sys.getService("testservice")
      val testComponent = sys.getComponent("testcomponent")

      probe.send(testService.get, RegisterShutdownListener(probe.ref))
      probe.send(testComponent.get, RegisterShutdownListener(probe.ref))

      sys.stop
      val results = probe.receiveN(2, timeout.duration)

      results must have size(2)
      TestHarness.log.foreach(_.debug(s"Results $results"))
      results must contain(be_==("GotShutdown")).foreach
    }
  }
}
