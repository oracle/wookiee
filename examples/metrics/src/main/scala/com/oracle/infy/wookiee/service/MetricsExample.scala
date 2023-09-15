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

package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.component.metrics.TimerContext
import com.oracle.infy.wookiee.component.metrics.metrictype.{Counter, Gauge, Histogram, Meter, Timer}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.service.messages.Ready

import scala.concurrent._
import scala.util.Random

/**
 * Creates each type of metric and then updates them every 30 seconds.  The current state of the metrics can be
 * viewed by hitting the metrics endpoint. The metrics will be shown under the metrics object of the returned json:
 * <br>
 * <pre>
 * ...
 * "metrics": {
 *   "MetricsExample.counter": 1,
 *   "MetricsExample.gauge": 4,
 *   "MetricsExample.histogram": {...},
 *   "MetricsExample.meter": {...},
 *   "MetricsExample.timer": {...}
 * }
 * ...
 * </pre>
 */
class MetricsExample extends Service {
  val counter: Counter = Counter("MetricsExample.counter")
  val gauge: Gauge = Gauge("MetricsExample.gauge")
  val timer: Timer = Timer("MetricsExample.timer")
  val histogram: Histogram = Histogram("MetricsExample.histogram")
  val meter: Meter = Meter("MetricsExample.meter")

  val rand = new Random()

  /**
   * When the Ready message is received, start generating metrics
   */
  override def serviceReceive: Receive = ({
    case Ready() =>
      log.info("Ready message received, start generating metrics")
      val thread = new Thread {
        override def run(): Unit = {
          startGeneratingMetrics(generateMetrics)
        }
      }
      thread.start()
  }: Receive) orElse super.serviceReceive

  /**
   * Call the given callback function every 30 seconds
   */
  def startGeneratingMetrics(callback: () => Unit): Unit = {
    while(true) {
      // Increment the number of times the callback is called
      counter.incr
      callback()
      Thread sleep 30000
    }
  }

  /**
   * Updates the various metrics for this service
   */
  def generateMetrics(): Unit = {
    val timerContext = TimerContext()
    // Sleep for up to 20 seconds
    val t = rand.nextInt(20000)
    Thread sleep t

    // Record the amount of time spent sleeping
    timer.record(timerContext.stop)
    // Set the gauge to the number of seconds slept
    gauge.update(t / 1000)
    // Update the histogram with the number of milliseconds slept
    histogram.update(t)
    // Mark the completion of this function
    meter.mark
  }

  /**
   * Returns the health of this service
   */
  override def getHealth: Future[HealthComponent] = {
    Future {
      HealthComponent("MetricsExample", ComponentState.NORMAL, "MetricsExample healthy")
    }
  }
}

