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

package com.webtrends.harness.component.kafka

import akka.actor.ActorRef
import akka.testkit.TestProbe
import com.webtrends.harness.component.kafka.config.KafkaTestConfig
import com.webtrends.harness.component.zookeeper.ZookeeperManager
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

/**
 * @author Spencer Wood
 */
@RunWith(classOf[JUnitRunner])
class KafkaManagerSpec extends SpecificationLike with NoTimeConversions {
  val sys = TestHarness(KafkaTestConfig.config, None, Some(Map("wookiee-zookeeper" -> classOf[ZookeeperManager], "wookiee-kafka" -> classOf[KafkaManager])))
  implicit val system = TestHarness.system.get

  //Create our leader
  val workerManager = sys.getComponent("wookiee-kafka").get

  Thread.sleep(2000)

  sequential

  "KafkaManager" should {
    "be ready" in {
      Thread.sleep(1000)
      val probe = TestProbe()
      probe.send(workerManager, CheckHealth)
      probe.expectMsgPF() {
        case health: HealthComponent  =>
          health.state mustEqual ComponentState.NORMAL
        case _ =>
          false mustEqual true
      }
    }
    /*
    "be able to return its leader" in {
      val probe = TestProbe()
      probe.send(workerManager, KafkaManager.GetCoordinator)
      val result = probe.expectMsgClass(classOf[Option[ActorRef]])
      result.isDefined mustEqual true
    }
    */
  }

  step {

    sys.stop
  }
}
