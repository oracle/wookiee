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
import com.webtrends.harness.command.{CommandBeanHelper, CommandManager, ExecuteCommand, MapBean}
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.service.messages.Ready
import com.webtrends.harness.service.test.TestSystemActor.RegisterShutdownListener
import com.webtrends.harness.service.test.command.{WeatherCommand, WeatherData}
import com.webtrends.harness.service.test.{TestComponent, TestHarness, TestService}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.duration.Duration


class TestHarnessSpec extends WordSpecLike with Matchers with Inspectors {
  implicit val timeout: Timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val sys: TestHarness = TestHarness(ConfigFactory.empty(), Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent])))
  implicit val actorSystem: ActorSystem = TestHarness.system.get

  "test harness " should {
    "start up service manager " in {
      sys.serviceManager.get.isInstanceOf[ActorRef] shouldBe true
    }

    "load test service " in {
      val probe = TestProbe()
      val testService = sys.getService("testservice")
      assert(testService.isDefined, "Test service was not registered")
      probe.send(testService.get, Ready)
      Ready shouldBe probe.expectMsg(Ready)
    }

    "start up component manager " in {
      sys.componentManager.get.isInstanceOf[ActorRef] shouldBe true
    }

    "load test component " in {
      val probe = TestProbe()
      val testComponent = sys.getComponent("testcomponent")
      assert(testComponent.isDefined, "Test component was not registered")
      probe.send(testComponent.get, StatusRequest)
      TestComponent.ComponentMessage shouldBe (probe.expectMsg(TestComponent.ComponentMessage))
    }

    "load command manager and commands size equals 1" in {
      val commandManager = sys.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      CommandManager.getCommands().get.size equals 1
    }

    "load test command and get weather" in {
      val probe = TestProbe()
      val commandManager = sys.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      probe.send(commandManager.get, ExecuteCommand(WeatherCommand.CommandName, timeout = timeout,
        bean = CommandBeanHelper.createInput[WeatherData](
          MapBean(Map[String, Any](
            "name" -> "Seattle, WA",
            "location" -> "47.608013,-122.335167",
            "mode" -> "current")
          ))))

      probe.expectMsgPF[String](Duration(5, TimeUnit.SECONDS)) {
        case r: String =>
          TestHarness.log.debug(s"Weather: $r")
          r
        case _ =>  "Weather data not found"
      }
    }


    "shutdown services and components" in {
      val probe = TestProbe()
      val testService = sys.getService("testservice")
      val testComponent = sys.getComponent("testcomponent")

      probe.send(testService.get, RegisterShutdownListener(probe.ref))
      probe.send(testComponent.get, RegisterShutdownListener(probe.ref))

      sys.stop

      val results = probe.receiveN(2, timeout.duration)
      TestHarness.log.debug(s"Results $results")
      results should have size (2)
      results should contain("GotShutdown")
    }
  }
}
