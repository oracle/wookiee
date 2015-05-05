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

import com.webtrends.harness.component.metrics.messages.{MeterObservation, TimerObservation, MetricObservation, RemoveMetric}
import com.webtrends.harness.component.metrics.metrictype.{Timer, Metric, Meter}

private[metrics] class MetricsService {

  /**
   * Remove the given metric from the registry
   * @param metric
   */
  def remove(metric: Metric): Unit = MetricsEventBus.publish(RemoveMetric(metric))

  /**
   * Record the passed observation
   * @param observation the observation to record
   */
  def record(observation: MetricObservation): Unit = MetricsEventBus.publish(observation)

  /**
   * Time the application of this function
   * @param metric
   * @param   f function that is timed
   * @tparam A
   * @return
   */
  def time[A](metric: Timer)(f: => A): A = {
    val ctx = TimerContext()
    try {
      f
    } finally {
      record(TimerObservation(metric, ctx.stop))
    }
  }

  /**
   * Meter the application of this function
   * @param metric
   * @param   f function that is timed
   * @tparam A
   * @return
   */
  def meter[A](metric: Meter)(f: => A): A = {
    try {
      f
    } finally {
      record(MeterObservation(metric))
    }
  }
}

object MetricsService {
  def apply(): MetricsService = new MetricsService
}

trait MetricsAdapter {

  private lazy val metricsService = MetricsService()

  /**
   * Remove the given metric from the registry
   * @param metric
   */
  def remove(metric: Metric): Unit = metricsService.remove(metric)

  /**
   * Record the passed observation
   * @param observation the observation to record
   */
  def record(observation: MetricObservation): Unit = metricsService.record(observation)

  /**
   * Time the application of this function
   * @param metric
   * @param   f function that is timed
   * @tparam A
   * @return
   */
  def time[A](metric: Timer)(f: => A): A = metricsService.time(metric)(f)

  /**
   * Meter the application of this function
   * @param metric
   * @param   f function that is timed
   * @tparam A
   * @return
   */
  def meter[A](metric: Meter)(f: => A): A = metricsService.meter(metric)(f)
}


