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

package com.webtrends.harness.component.netty.http

import java.util.concurrent.TimeUnit

import akka.testkit.TestProbe
import akka.util.Timeout
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.component.netty.{NettyManager, NettyTestConfig}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import org.specs2.mutable.SpecificationWithJUnit

/**
 * Created by wallinm on 1/8/15.
 */
class NettyManagerSpec extends SpecificationWithJUnit {
  implicit val timeout = Timeout(2000, TimeUnit.MILLISECONDS)
  val sys = TestHarness(NettyTestConfig.config, None, Some(Map("wookiee-netty" -> classOf[NettyManager])))

  implicit val actorSystem = TestHarness.system.get

  val nettyManager = sys.getComponent("wookiee-netty").get
  val probe = TestProbe()

  "NettyManager" should {
    "be ready" in {
      probe.send(nettyManager, CheckHealth)
      probe.expectMsgPF() {
        case health: HealthComponent =>
          health.state mustEqual ComponentState.NORMAL
        case _ =>
          false mustEqual true
      }
    }
    
    "get ping response" in {
      HttpRequest.Get("http://127.0.0.1:9091/ping").contains("pong:")
    }
  }
}
