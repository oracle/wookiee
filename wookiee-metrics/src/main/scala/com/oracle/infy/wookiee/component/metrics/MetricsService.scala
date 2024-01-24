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

import com.codahale.metrics.MetricFilter
import com.oracle.infy.wookiee.component.metrics.messages.{MeterObservation, MetricObservation, RemoveMatchingMetric, RemoveMetric, TimerObservation}
import com.oracle.infy.wookiee.component.metrics.metrictype.{Meter, Metric, Timer}

private[metrics] class MetricsService {

  /**
    * Remove the given metric from the registry
    */
  def remove(metric: Metric): Unit = MetricsEventBus.publish(RemoveMetric(metric))


  def remove(filter: MetricFilter, metric: Metric): Unit = MetricsEventBus.publish(RemoveMatchingMetric(filter, metric))

  /**
    * Record the passed observation
    * @param observation the observation to record
    */
  def record(observation: MetricObservation): Unit = MetricsEventBus.publish(observation)

  /**
    * Time the application of this function
    * @param   f function that is timed
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
    * @param   f function that is timed
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
    */
  def remove(metric: Metric): Unit = metricsService.remove(metric)

  /**
   * Remove the metrics matching the given metricFilter
   */
  def remove(metricFilter: MetricFilter, metric: Metric): Unit = metricsService.remove(metricFilter, metric)

  /**
    * Record the passed observation
    * @param observation the observation to record
    */
  def record(observation: MetricObservation): Unit = metricsService.record(observation)

  /**
    * Time the application of this function
    * @param   f function that is timed
    */
  def time[A](metric: Timer)(f: => A): A = metricsService.time(metric)(f)

  /**
    * Meter the application of this function
    * @param   f function that is timed
    */
  def meter[A](metric: Meter)(f: => A): A = metricsService.meter(metric)(f)
}
