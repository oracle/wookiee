/*
 * Copyright 2015 Oracle (http://www.Oracle.com)
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
package com.oracle.infy.wookiee.component.metrics

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.metrics.messages.CounterObservation
import com.oracle.infy.wookiee.component.metrics.metrictype.Counter
import com.oracle.infy.wookiee.component.metrics.monitoring.MonitoringSettings
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.jdk.CollectionConverters._

class MetricsManagerSpec
    extends TestKit(
      ActorSystem(
        "test",
        ConfigFactory.parseString("""
          wookiee-metrics {
             application-name = "Oracle Harness"
             metric-prefix = workstations
             jmx {
              enabled = false
              port = 9999
             }
             graphite {
               enabled = false
               host = ""
               port = 2003
               interval = 5
               vmmetrics=true
               regex=""
             }
           }
        """)
      )
    )
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  "Metrics" should {
    val probe = new TestProbe(system)
    val actor = TestActorRef[MetricsActor](
      MetricsActor.props(MonitoringSettings(system.settings.config.getConfig("wookiee-metrics")))
    )

    "be able to start properly" in {
      MetricBuilder.registry must not be null
      MetricBuilder.jvmRegistry must not be null
      actor.underlyingActor.graphiteReporter mustBe None
      actor.underlyingActor.jvmGraphiteReporter mustBe None
    }

    "have a health" in {
      MetricsActor.health.name mustBe "metrics"
    }

    "be able to receive a metric observation and record it" in {
      val met = Counter("group.subgroup.count")
      probe.send(actor, CounterObservation(met, 1))
      MetricBuilder.registry.getCounters.containsKey(met.name) mustBe true
    }

    "be able to receive a metric observation, record it, and then remove it" in {
      val met = Counter("group.subgroup.count2")
      probe.send(actor, CounterObservation(met, 1))
      MetricBuilder.registry.getCounters.containsKey(met.name) mustBe true

      MetricBuilder.remove(met) mustBe true
      MetricBuilder.registry.getCounters.containsKey(met.name) mustBe false
    }

    "return json metrics" in {
      probe.send(actor, StatusRequest())
      val result = probe.expectMsgClass(classOf[JValue])

      val groups = compact(render(result \ "system" \ "metrics" \ "group.subgroup.count"))
      groups mustEqual "1"
    }

    "rename jvm time gauge" in {
      !MetricBuilder.jvmRegistry.getGauges.keySet().asScala.exists(_.endsWith(".time")) mustBe true
      MetricBuilder.jvmRegistry.getGauges.keySet().asScala.exists(_.endsWith(".gctime")) mustBe true
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
