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
import akka.testkit.{TestKit, TestProbe}
import com.oracle.infy.wookiee.component.metrics.messages._
import com.oracle.infy.wookiee.component.metrics.metrictype._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MetricSpec extends TestKit(ActorSystem("metricspec")) with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  val probe = new TestProbe(system)
  MetricsEventBus.subscribe(probe.ref)

  "metrics " should {
    "allow for counters" in {
      val metric = Counter("group.metrics.name.counter")
      metric.incr

      val obs = CounterObservation(metric, 1)
      obs mustBe lookForObs(obs)

      metric.incr(5)
      val obs2 = CounterObservation(metric, 5)
      obs2 mustBe lookForObs(obs2)
    }

    "allow for gauges" in {
      val metric = Gauge("group.metrics.name.guage")
      metric.update(3.25f)
      val obs = GaugeObservation(metric, 3.25f)
      obs mustBe lookForObs(obs)
    }

    "allow for histograms" in {
      val metric = Histogram("group.metrics.name.histogram")
      metric.update(15)
      val obs = HistogramObservation(metric, 15)
      obs mustBe lookForObs(obs)
    }

    "allow for meters" in {
      val metric = Meter("group.metrics.name.meter")
      metric.mark
      val obs = MeterObservation(metric)
      obs mustBe lookForObs(obs)

      metric.mark(5)
      val obs2 = MeterObservation(metric, 5)
      obs2 mustBe lookForObs(obs2)

      metric.meter() {}

      probe.expectMsgClass(classOf[MeterObservation]).isInstanceOf[MeterObservation] mustBe true
    }

    "allow for timers" in {
      val metric = Timer("group.metrics.name.timer")

      metric.time() {}

      probe.expectMsgClass(classOf[TimerObservation]).isInstanceOf[TimerObservation] mustBe true
    }
  }

  def lookForObs(obs: MetricObservation): MetricObservation = {
    var out: Option[MetricObservation] = None
    val tries = 5
    for {
      _ <- 0.to(tries)
      if out.isEmpty
    } yield {
      try {
        out = Some(probe.expectMsg(obs))
      } catch {
        case _: Throwable =>
      }
    }
    out.getOrElse(throw new AssertionError(s"Failed to get back observation: [$obs]"))
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
