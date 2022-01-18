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

import com.codahale.metrics._
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, DoubleMode, Formats, JsonAST, JsonDSL}

import scala.jdk.CollectionConverters._
import scala.collection._

class MetricsWriter extends JsonDSL with DoubleMode {

  implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  def getMetrics(): JValue = {
    "system" ->
      ("jvm" -> getVmMetrics) ~
        ("metrics" -> getCustomMetrics)
  }

  def getVmMetrics: JObject = {
    val t = SortedMap(MetricBuilder.jvmRegistry.getMetrics.asScala.toList: _*).groupBy {
      // Grab the actual type
      _._2
        .getClass
        .getName
        .replace("com.codahale.metrics.jvm.", "")
        .replace("com.codahale.metrics.", "")
        .split("\\$")(0) match {
        case "JmxAttributeGauge"         => "buffers"
        case "GarbageCollectorMetricSet" => "garbage-collection"
        case "MemoryUsageGaugeSet"       => "memory"
        case "ThreadStatesGaugeSet"      => "thread-states"
      }
    }

    t.map {
        case (w: String, mp: SortedMap[String, Metric]) =>
          w ->
            mp.map {
                case (s: String, m: Metric) =>
                  s -> processMetric(m)
              }
              .foldLeft(JObject(Nil))(_ ~ _)
      }
      .foldLeft(JObject(Nil))(_ ~ _)
  }

  def getCustomMetrics: JObject = {
    val t = SortedMap(MetricBuilder.registry.getMetrics.asScala.toList: _*)

    if (t.isEmpty) {
      JObject(Nil)
    } else {
      t.map {
          case (w: String, m: Metric) =>
            w -> processMetric(m)
        }
        .foldLeft(JObject(Nil))(_ ~ _)
    }
  }

  def processMetric(metric: Metric): JValue = {

    val Hist = classOf[Histogram]
    val Count = classOf[Counter]
    val Mtr = classOf[Meter]
    val Gau = classOf[Gauge[_]]
    val UGau = classOf[UpdatableGauge[_]]
    val Tim = classOf[Timer]

    metric.getClass match {
      case Hist =>
        processHistogram(metric.asInstanceOf[Histogram])
      case Count =>
        processCounter(metric.asInstanceOf[Counter])
      case Mtr =>
        processMeter(metric.asInstanceOf[Meter])
      case Gau =>
        processGauge(metric.asInstanceOf[Gauge[_]])
      case UGau =>
        processGauge(metric.asInstanceOf[UpdatableGauge[_]])
      case Tim =>
        processTimer(metric.asInstanceOf[Timer])
      case _ => // This is a metric set which contains functions as the metric value
        if (Hist.isInstance(metric)) {
          processHistogram(metric.asInstanceOf[Histogram])
        } else if (Count.isInstance(metric)) {
          processCounter(metric.asInstanceOf[Counter])
        } else if (Mtr.isInstance(metric)) {
          processMeter(metric.asInstanceOf[Meter])
        } else if (Gau.isInstance(metric)) {
          processGauge(metric.asInstanceOf[Gauge[_]])
        } else if (UGau.isInstance(metric)) {
          processGauge(metric.asInstanceOf[UpdatableGauge[_]])
        } else {
          processTimer(metric.asInstanceOf[Timer])
        }
    }
  }

  def processHistogram(metric: Histogram): JObject = {
    ("count" -> metric.getCount) ~
      writeSampling(metric)
  }

  def processCounter(metric: Counter): JValue = {
    metric.getCount
  }

  def processGauge(metric: Gauge[_]): JValue = {
    evaluateGauge(metric)
  }

  def processMeter(metric: com.codahale.metrics.Meter): JObject = {
    ("event_type" -> metric.getCount) ~
      writeMeteredFields(metric)
  }

  def processTimer(metric: com.codahale.metrics.Timer): JObject = {
    ("duration" ->
      ("unit" -> "nanoseconds") ~
        writeSampling(metric)) ~
      ("rate" -> writeMeteredFields(metric))
  }

  private def evaluateGauge(gauge: com.codahale.metrics.Gauge[_]): JValue = {
    try {
      matchAny(gauge.getValue)
    } catch {
      case e: Throwable =>
        string2jvalue("Error evaluating the gauge: " + e.getMessage)
    }
  }

  private def writeSampling(metric: Sampling): JObject = {
    val snapshot = metric.getSnapshot

    ("min" -> snapshot.getMin) ~
      ("max" -> snapshot.getMax) ~
      ("mean" -> snapshot.getMean)
  }

  private def writeMeteredFields(metric: Metered): JObject = {
    ("unit" -> "events/second") ~
      ("count" -> metric.getCount) ~
      ("mean" -> metric.getMeanRate) ~
      ("m1" -> metric.getOneMinuteRate) ~
      ("m5" -> metric.getFiveMinuteRate) ~
      ("m15", metric.getFifteenMinuteRate)
  }

  private def matchAny(num: Any): JValue = {
    try {
      (num: Any) match {
        case z: Boolean => boolean2jvalue(z)
        case b: Byte    => int2jvalue(b.toInt)
        case c: Char    => int2jvalue(c.toInt)
        case s: Short   => int2jvalue(s.toInt)
        case i: Int     => int2jvalue(i)
        case j: Long    => long2jvalue(j)
        case f: Float   => float2jvalue(f)
        case d: Double  => bigdecimal2jvalue(d)
        case st: String => string2jvalue(st)
        case _: AnyRef  => JsonAST.JNull
      }
    } catch {
      case e: Throwable =>
        string2jvalue("Error evaluating the value: " + e.getMessage)
    }
  }
}
