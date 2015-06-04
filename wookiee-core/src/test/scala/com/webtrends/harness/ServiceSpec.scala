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
import org.specs2.mutable.SpecificationLike

case class TestClass(val name: String, val value: Int)


class ServiceSpec extends TestKit(ActorSystem("harness")) with SpecificationLike {

  val act = TestActorRef(new TestService)
  //val httpAct = TestActorRef(new TestHttpService)

  "services " should {

    " be able to be loaded and pinged" in {
      val probe = TestProbe()
      probe.send(act, Ping)
      Pong must beEqualTo(probe.expectMsg(Pong))
    }

    " be able to be loaded and sent a ready message" in {
      val probe = TestProbe()
      probe.send(act, Ready)
      Ready must beEqualTo(probe.expectMsg(Ready))
    }

    " be able to be loaded and checked" in {
      val probe = TestProbe()
      probe.send(act, CheckHealth)
      val comp = HealthComponent("testservice", ComponentState.NORMAL, "test")
      comp.addComponent(HealthComponent("childcomponent", ComponentState.DEGRADED, "test"))

      comp must beEqualTo(probe.expectMsg(comp))
    }

    //todo only HttpService should be able to do this
    /*" be able to determine if it does support http " in {
      val probe = TestProbe()
      probe.send(httpAct, GetMetaDetails)
      val meta = probe.expectMsg(PluginMetaDetails(true))
      meta.supportsHttp must beEqualTo(true)
    }*/

    " be able to determine if it does not support http " in {
      val probe = TestProbe()
      probe.send(act, GetMetaDetails)
      val meta = probe.expectMsg(ServiceMetaDetails(false))
      meta.supportsHttp must beEqualTo(false)
    }

    //todo only HttpPlugin should be able to do this
    /*" be able to be loaded respond to an http request" in {
      // Send a message and ask for a response
      val probe = TestProbe()
      probe.send(httpAct, Get("/plugin/test"))
      val resp = probe.expectMsg(HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), "Yeeeha!")))
      resp.status must beEqualTo(StatusCodes.OK)
    }*/
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}

