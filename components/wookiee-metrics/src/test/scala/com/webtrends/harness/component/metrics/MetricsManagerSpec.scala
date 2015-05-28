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
package com.webtrends.harness.component.metrics

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.TestKitSpecificationWithJUnit
import com.webtrends.harness.component.metrics.messages.CounterObservation
import com.webtrends.harness.component.metrics.metrictype.Counter
import com.webtrends.harness.component.metrics.monitoring.MonitoringSettings
import com.webtrends.harness.health.HealthComponent
import com.webtrends.harness.service.messages.CheckHealth

class MetricsManagerSpec extends TestKitSpecificationWithJUnit(ActorSystem("test", ConfigFactory.parseString( """
          wookiee-metrics {
             application-name = "Webtrends Harness"
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
        """))) {

  val probe = new TestProbe(system)
  val actor = TestActorRef[MetricsActor] (MetricsActor.props(MonitoringSettings(system.settings.config.getConfig("wookiee-metrics"))))

  "Metrics" should {
    "be able to start properly" in {
      MetricBuilder.registry must not be equalTo(null)
      MetricBuilder.jvmRegistry must not be equalTo(null)
      actor.underlyingActor.graphiteReporter must be equalTo (None)
      actor.underlyingActor.jvmGraphiteReporter must be equalTo (None)
    }

    "be able to return it's health" in {
      probe.send(actor, CheckHealth)
      val msg = probe.expectMsgClass(classOf[HealthComponent])
      msg.name must be equalTo "metrics"
    }

    "be able to receive a metric observation and record it" in {
      val met = Counter("group.subgroup.count")
      probe.send(actor, CounterObservation(met, 1))
      MetricBuilder.registry.getCounters.containsKey(met.name) must beTrue
    }

    "be able to receive a metric observation, record it, and then remove it" in {
      val met = Counter("group.subgroup.count2")
      probe.send(actor, CounterObservation(met, 1))
      MetricBuilder.registry.getCounters.containsKey(met.name) must beTrue

      MetricBuilder.remove(met) must beTrue
      MetricBuilder.registry.getCounters.containsKey(met.name) must beFalse
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}