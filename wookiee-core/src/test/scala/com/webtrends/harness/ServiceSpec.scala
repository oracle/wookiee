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

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages._
import com.webtrends.harness.service.meta.ServiceMetaDetails
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

case class TestClass(name: String, value: Int)

class ServiceSpec extends TestKit(ActorSystem("harness")) with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  val act: TestActorRef[TestService] = TestActorRef(new TestService)

  "Services " should {

    " be able to be loaded and pinged" in {
      val probe = TestProbe()
      probe.send(act, Ping)
      Pong mustBe probe.expectMsg(Pong)
    }

    " be able to be loaded and sent a ready message" in {
      val probe = TestProbe()
      probe.send(act, Ready)
      Ready mustBe probe.expectMsg(Ready)
    }

    " be able to be loaded and checked" in {
      val probe = TestProbe()
      probe.send(act, CheckHealth)
      val comp = HealthComponent("testservice", ComponentState.NORMAL, "test")
      comp.addComponent(HealthComponent("childcomponent", ComponentState.DEGRADED, "test"))

      comp mustBe probe.expectMsg(comp)
    }

    " be able to determine if it does not support http " in {
      val probe = TestProbe()
      probe.send(act, GetMetaDetails)
      val meta = probe.expectMsg(ServiceMetaDetails(false))
      meta.supportsHttp mustBe false
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

