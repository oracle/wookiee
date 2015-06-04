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
package com.webtrends.harness.component.messages

import java.util.concurrent.TimeUnit
import com.webtrends.harness.component.metrics.messages._
import com.webtrends.harness.component.metrics.metrictype._
import org.specs2.mutable.SpecificationWithJUnit

class ObservationSpec extends SpecificationWithJUnit {

  "metric observations " should {
    "allow for counters" in {
      val counter = CounterObservation(Counter("group.subgroup.name.scope"), 10)
      counter.delta must beEqualTo(10)
    }

    "allow for gauges" in {
      val gauge = GaugeObservation(Gauge("group.subgroup.name.scope"), 10)
      gauge.value must beEqualTo(10)
    }

    "allow for histograms" in {
      val histo = HistogramObservation(Histogram("group.subgroup.name.scope"), 10)
      histo.value must beEqualTo(10)
    }

    "allow for meters" in {
      val meter = MeterObservation(Meter("group.subgroup.name.scope"), 10)
      meter.events must beEqualTo(10)
    }

    "allow for timers" in {
      val timer = TimerObservation(Timer("group.subgroup.name.scope"), 10, TimeUnit.SECONDS)
      timer.elapsed must beEqualTo(10)
    }
  }
}
