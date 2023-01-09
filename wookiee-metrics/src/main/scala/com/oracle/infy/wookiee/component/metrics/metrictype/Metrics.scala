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
package com.oracle.infy.wookiee.component.metrics.metrictype

import com.codahale.metrics.jvm.FileDescriptorRatioGauge
import com.oracle.infy.wookiee.component.metrics.MetricsAdapter
import com.oracle.infy.wookiee.component.metrics.messages.{
  CounterObservation,
  GaugeObservation,
  HistogramObservation,
  MeterObservation,
  TimerObservation
}

import java.util.concurrent.TimeUnit

trait Metric {
  def name: String
}

case class Counter(name: String) extends Metric with MetricsAdapter {

  /**
    * Increment the counter by one
    */
  def incr: Unit = incr(1)

  /**
    * Increment the counter by the given delta
    * @param delta the value to increment by
    */
  def incr(delta: Int): Unit = record(CounterObservation(this, delta))
}

case class Gauge(name: String) extends Metric with MetricsAdapter {

  /**
    * Set the current value for the gauge
    * @param value the value to set
    */
  def update(value: Float): Unit = record(GaugeObservation(this, value))
}

case class Histogram(name: String, biased: Boolean = false) extends Metric with MetricsAdapter {

  /**
    * Update the current value for the histogram
    * @param value the value to set
    */
  def update(value: Long): Unit = record(HistogramObservation(this, value))
}

case class Meter(name: String) extends Metric with MetricsAdapter {

  /**
    * Mark the occurrence of an event.
    */
  def mark: Unit = mark(1L)

  /**
    * Mark the occurrence of a given number of events.
    *
    * @param value the number of events
    */
  def mark(value: Long): Unit = record(MeterObservation(this, value))

  /**
    * Meter the function
    */
  def meter[A]()(f: => A): A = meter(this)(f)
}

case class Timer(name: String) extends Metric with MetricsAdapter {

  /**
    * Time the function
    */
  def time[A]()(f: => A): A = time(this)(f)

  /**
    * Record a timed event
    * @param unit the time unit, defaults to nanoseconds
    */
  def record(time: Long, unit: TimeUnit = TimeUnit.NANOSECONDS): Unit = record(TimerObservation(this, time, unit))
}

case class WookieeFileRatioGauge() extends FileDescriptorRatioGauge() {
  override def toString: String = getRatio.toString
}

object Metric {

  /**
    * Concatenates elements to form a dotted name, eliding any null values or empty strings.
    *
    * @param name     the first element of the name
    * @param names    the remaining elements of the name
    * @return { @code name} and { @code names} concatenated by periods
    */
  def name(name: String, names: String*): String = {
    val builder: StringBuilder = new StringBuilder
    append(builder, name)
    if (names != null) {
      for (s <- names) {
        append(builder, s)
      }
    }
    builder.toString
  }

  private def append(builder: StringBuilder, part: String): Unit = {
    if (part != null && part.nonEmpty) {
      if (builder.nonEmpty) {
        builder.append('.')
      }
      builder.append(part)
    }
    ()
  }
}
