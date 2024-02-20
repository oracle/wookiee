/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.infy.wookiee.test

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.app.HarnessActorSystem
import com.oracle.infy.wookiee.command._
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.messages.Ready
import com.oracle.infy.wookiee.service.meta.ServiceMetaDataV2
import com.oracle.infy.wookiee.test.command.{WeatherCommand, WeatherData}
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

class TestHarnessSpec extends AnyWordSpecLike with Matchers with Inspectors {
  implicit val timeout: Timeout = Timeout(10000, TimeUnit.MILLISECONDS)

  val sys: TestHarness = TestHarness(
    HarnessActorSystem.renewConfigsAndClasses(None),
    Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent]))
  )

  // Ensure we can start up a second one without breaking
  val sys2: TestHarness = TestHarness(
    HarnessActorSystem.renewConfigsAndClasses(None),
    Some(Map("testservice" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent]))
  )
  implicit val actorSystem: ActorSystem = sys.system
  val actorSystem2: ActorSystem = sys2.system

  "test harnesses " should {
    "log logging classes successfully" in {
      LoggingAdapter.printLoggingClasses()
    }

    "start up service manager for both " in {
      sys.serviceManager.isDefined shouldBe true
      sys2.serviceManager.isDefined shouldBe true
    }

    "load both test services " in {
      def checkReady(sysToUse: TestHarness) = {
        val testService = sysToUse.getService("testservice")
        assert(testService.isDefined, "Test service was not registered")
        val ready =
          Await.result((testService.get.asInstanceOf[ServiceMetaDataV2].service ? Ready()).mapTo[Ready], 5.seconds)
        ready shouldBe Ready()
      }

      checkReady(sys)
      checkReady(sys2)
    }

    "start up component manager on both " in {
      sys.componentManager.isDefined shouldBe true
      sys2.componentManager.isDefined shouldBe true
    }

    "load test components " in {
      def loadComponent(sysToUse: TestHarness) = {
        val testComponent = sysToUse.getComponentV2("testcomponent")
        assert(testComponent.isDefined, "Test component was not registered")
        val reply = Await.result(testComponent.get ? StatusRequest, 5.seconds)
        TestComponent.ComponentMessage shouldBe reply
      }

      loadComponent(sys)
      loadComponent(sys2)
    }

    val bean = CommandBeanHelper.createInput[WeatherData](
      MapBean(Map[String, Any]("name" -> "Seattle, WA", "location" -> "47.608013,-122.335167", "mode" -> "current"))
    )

    "load command managers and commands size equals 1 for both" in {
      WookieeCommandExecutive.getMediator(sys.config).getCommand("TestCommand").isDefined shouldBe true
      WookieeCommandExecutive.getMediator(sys2.config).getCommand("TestCommand").isDefined shouldBe true
    }

    "load test command and get weather" in {
      val probe = TestProbe()
      val commandManager = sys.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      probe.send(commandManager.get, AddCommand("WeatherCommand", classOf[WeatherCommand]))
      probe.expectMsgType[ActorRef](Duration(15, TimeUnit.SECONDS))
      probe.send(commandManager.get, ExecuteCommand("WeatherCommand", bean, timeout))

      val reply = probe.expectMsgType[String](Duration(15, TimeUnit.SECONDS))
      reply.contains("47.608013") shouldBe true
      reply.contains("-122.335167") shouldBe true
    }

    "execute remote command logic" in {
      val probe = TestProbe()
      val commandManager = sys.commandManager
      assert(commandManager.isDefined, "Command Manager was not registered")
      probe.send(
        commandManager.get,
        ExecuteCommand[WeatherData, String]("WeatherCommand", bean, timeout)
      )

      val reply = probe.expectMsgType[String](Duration(15, TimeUnit.SECONDS))
      reply shouldBe "Weather: 47.608013,-122.335167|current"
    }

    "shutdown services and components" in {
      val shutdownsSeen = new AtomicInteger(0)
      val shutdownsSeen2 = new AtomicInteger(0)
      val probe1 = WookieeActor.actorOf(new WookieeActor {
        override protected def receive: Receive = {
          case "GotShutdown" =>
            shutdownsSeen.incrementAndGet()
            ()
        }
      })
      val probe2 = WookieeActor.actorOf(new WookieeActor {
        override protected def receive: Receive = {
          case "GotShutdown" =>
            shutdownsSeen2.incrementAndGet()
            ()
        }
      })
      val testService = sys.getService("testservice")
      val testComponent = sys.getComponentV2("testcomponent")
      val testService2 = sys2.getService("testservice")
      val testComponent2 = sys2.getComponentV2("testcomponent")

      testService.get.asInstanceOf[ServiceMetaDataV2].service !
        ShutdownListener.RegisterShutdownListener(probe1)
      testComponent.get ! ShutdownListener.RegisterShutdownListener(probe1)

      testService2.get.asInstanceOf[ServiceMetaDataV2].service !
        ShutdownListener.RegisterShutdownListener(probe2)
      testComponent2.get ! ShutdownListener.RegisterShutdownListener(probe2)
      Thread.sleep(500L)

      sys.stop()(actorSystem)
      sys2.stop()(actorSystem2)

      ThreadUtil.awaitEvent({ shutdownsSeen.get() == 2 })
      ThreadUtil.awaitEvent({ shutdownsSeen2.get() == 2 })
    }
  }
}
