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

package com.webtrends.harness.component.socko

import akka.actor.Props
import akka.testkit.TestProbe
import com.webtrends.harness.component.socko.handlers.TestSockoHandler
import com.webtrends.harness.component.socko.route.SockoRouteManager
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SockoManagerSpec extends SockoTestBase {

  "SockoManager" should {
    "be ready" in {
      val probe = TestProbe()
      probe.send(sockoManager, CheckHealth)
      probe.expectMsgPF() {
        case health: HealthComponent =>
          health.state mustEqual ComponentState.NORMAL
        case _ =>
          false mustEqual true
      }
    }
  }
}
