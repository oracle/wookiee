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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.command._
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.service.messages.Ready
import com.webtrends.harness.service.test.TestSystemActor.RegisterShutdownListener
import com.webtrends.harness.service.test.{TestComponent, TestHarness, TestService}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.duration.Duration


class TestHarnessSpec extends WordSpecLike with Matchers with Inspectors {
  implicit val timeout: Timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val sys: TestHarness = TestHarness(ConfigFactory.empty(), Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent])), port = 2551)
  // Ensure we can start up a second one without breaking
  val sys2: TestHarness = TestHarness(ConfigFactory.empty(), Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent])), port = 2553)
  implicit val actorSystem: ActorSystem = TestHarness.system(2551).get
  val actorSystem2: ActorSystem = TestHarness.system(2553).get

  "test harnesses " should {
    "start up service manager for both " in {
      sys.serviceManager.get.isInstanceOf[ActorRef] shouldBe true
      sys2.serviceManager.get.isInstanceOf[ActorRef] shouldBe true
    }

    "load both test services " in {
      def checkReady(sysToUse: TestHarness) = {
        val probe = TestProbe()
        val testService = sysToUse.getService("testservice")
        assert(testService.isDefined, "Test service was not registered")
        probe.send(testService.get, Ready)
        Ready shouldBe probe.expectMsg(Ready)
      }

      checkReady(sys)
      checkReady(sys2)
    }

    "start up component manager on both " in {
      sys.componentManager.get.isInstanceOf[ActorRef] shouldBe true
      sys2.componentManager.get.isInstanceOf[ActorRef] shouldBe true
    }

    "load test components " in {
      def loadComponent(sysToUse: TestHarness) = {
        val probe = TestProbe()
        val testComponent = sysToUse.getComponent("testcomponent")
        assert(testComponent.isDefined, "Test component was not registered")
        probe.send(testComponent.get, StatusRequest)
        TestComponent.ComponentMessage shouldBe probe.expectMsg(TestComponent.ComponentMessage)
      }

      loadComponent(sys)
      loadComponent(sys2)
    }

    "load command managers and commands size equals 1 for both" in {
      val probe1 = new TestProbe(actorSystem)
      val probe2 = new TestProbe(actorSystem2)
      val commandManager = sys.commandManager
      val commandManager2 = sys2.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      assert(commandManager2.isDefined, "Command Manager was not registered")

      probe1.send(commandManager.get, GetCommands())
      val commands1 = probe1.expectMsgType[Map[String, ActorRef]]
      commands1.size shouldBe 1

      probe2.send(commandManager2.get, GetCommands())
      val commands2 = probe2.expectMsgType[Map[String, ActorRef]]
      commands2.size shouldBe 1
    }

    "load test command and get weather" in {
      val probe = TestProbe()
      val commandManager = sys.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      probe.send(commandManager.get, AddCommand("WeatherCommand", classOf[WeatherCommand]))
      probe.expectMsgType[ActorRef](Duration(15, TimeUnit.SECONDS))
      probe.send(commandManager.get, ExecuteCommand("WeatherCommand", timeout = timeout,
        bean = CommandBeanHelper.createInput[WeatherData](
          MapBean(Map[String, Any](
            "name" -> "Seattle, WA",
            "location" -> "47.608013,-122.335167",
            "mode" -> "current")
          ))))

      val reply = probe.expectMsgType[String](Duration(15, TimeUnit.SECONDS))
      reply.contains("47.608013") shouldBe true
      reply.contains("-122.335167") shouldBe true
    }


    "shutdown services and components" in {
      val probe1 = new TestProbe(actorSystem)
      val probe2 = new TestProbe(actorSystem2)
      val testService = sys.getService("testservice")
      val testComponent = sys.getComponent("testcomponent")
      val testService2 = sys2.getService("testservice")
      val testComponent2 = sys2.getComponent("testcomponent")

      probe1.send(testService.get, RegisterShutdownListener(probe1.ref))
      probe1.send(testComponent.get, RegisterShutdownListener(probe1.ref))
      probe2.send(testService2.get, RegisterShutdownListener(probe2.ref))
      probe2.send(testComponent2.get, RegisterShutdownListener(probe2.ref))

      sys.stop(Some(2551))
      sys2.stop(Some(2553))

      val results = probe1.receiveN(2, timeout.duration)
      val results2 = probe2.receiveN(2, timeout.duration)
      TestHarness.log.debug(s"Results $results,  $results2")
      results should have size 2
      results should contain("GotShutdown")
      results2 should have size 2
      results2 should contain("GotShutdown")
    }
  }
}
