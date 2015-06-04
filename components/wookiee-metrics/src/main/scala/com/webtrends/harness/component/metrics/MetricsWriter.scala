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

import com.codahale.metrics._
import net.liftweb.json.JsonAST.render
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers
import scala.collection.JavaConversions._
import scala.collection._
import scala.collection.convert.Wrappers.JMapWrapper

class MetricsWriter {

  implicit val formats = net.liftweb.json.DefaultFormats ++ JodaTimeSerializers.all

  def getMetrics(includeJvm: Boolean): JValue = {
    "system" ->
      ("jvm" -> getVmMetrics) ~
        ("metrics" -> getCustomMetrics)
  }

  def getVmMetrics: JObject = {
    val t = SortedMap(MetricBuilder.jvmRegistry.getMetrics.to: _*).groupBy {
      // Grab the actual type
      _._2.getClass.getName.replace("com.codahale.metrics.jvm.", "").replace("com.codahale.metrics.", "").split("\\$")(0) match {
        case "JmxAttributeGauge" => "buffers"
        case "GarbageCollectorMetricSet" => "garbage-collection"
        case "MemoryUsageGaugeSet" => "memory"
        case "ThreadStatesGaugeSet" => "thread-states"
      }
    }

    //tf(SortedMap(JMapWrapper(t).to:_*))
    SortedMap(JMapWrapper(t).to: _*).map { w =>
      w._1 ->
        w._2.map { s =>
          s._1 -> processMetric(s._2)
        }.foldLeft(JObject(Nil))(_ ~ _)
    }.foldLeft(JObject(Nil))(_ ~ _) //.reduceLeft(_ ~ _)
  }

  def getCustomMetrics(): JObject = {
    val t = SortedMap(MetricBuilder.registry.getMetrics.to: _*)

    if (t.isEmpty) {
      JObject(Nil)
    }
    else {
      t.map {
        w => w._1 -> processMetric(w._2)
      }.foldLeft(JObject(Nil))(_ ~ _)
    }
  }

  def processMetric(metric: Metric): JValue = {

    val Hist = classOf[Histogram]
    val Count = classOf[Counter]
    val Mtr = classOf[Meter]
    val Gau = classOf[Gauge[_]]
    val UGau = classOf[UpdatableGauge[_]]
    val Tim = classOf[Timer]
    val Set = classOf[MetricSet]

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
        }
        else if (Count.isInstance(metric)) {
          processCounter(metric.asInstanceOf[Counter])
        }
        else if (Mtr.isInstance(metric)) {
          processMeter(metric.asInstanceOf[Meter])
        }
        else if (Gau.isInstance(metric)) {
          processGauge(metric.asInstanceOf[Gauge[_]])
        }
        else if (UGau.isInstance(metric)) {
          processGauge(metric.asInstanceOf[UpdatableGauge[_]])
        }
        else {
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
        writeSampling(metric)
      ) ~
      ("rate" -> writeMeteredFields(metric))
  }

  private def tf(map: Map[String, Any]): JObject = {
    val met = classOf[Metric]

    map.groupBy(g => g._1.split("\\.")(0)).map { w =>
      val key = w._1
      w._2.map { i =>
        val obj = i._2 match {
          case m if met.isAssignableFrom(i._2.getClass) =>
            if (i._1.contains(key.concat("."))) {
              val k = i._1.replace(key.concat("."), "")
              //val subMap = i._2.asInstanceOf[Map[String,Any]]
              //if (met.isAssignableFrom(i._2.getClass)) {
              //(key -> subMap.map { s => k -> processMetric(subMap.values.last.asInstanceOf[Metric])})//.reduceLeft(_ ~ _))
              //JField(key, JField(k, processMetric(subMap.values.last.asInstanceOf[Metric])))
              //(key -> processMetric(subMap.values.last.asInstanceOf[Metric])
              //k -> processMetric(subMap.values.last.asInstanceOf[Metric])
              //)
              if (met.isAssignableFrom(i._2.getClass) && !k.contains(".")) {
                //k -> processMetric(m.asInstanceOf[Metric])
                //val sm = Map(k -> i._2.asInstanceOf[Metric])
                //val zz = x(key, k, i._2.asInstanceOf[Metric])
                //(key -> sm.map {s => x(s._1, s._2)})
                //val yy = key -> processMetric(m.asInstanceOf[Metric])
                //val ff = (key -> (k -> processMetric(m.asInstanceOf[Metric])))
                //key -> processMetric(m.asInstanceOf[Metric])
                key -> tf(Map(k -> i._2.asInstanceOf[Metric]))
              }
              else {
                key -> tf(Map(k -> i._2))
              }
            }
            else {
              key -> processMetric(m.asInstanceOf[Metric])
            }
          case _ =>
            val b = i._2.asInstanceOf[Map[String, Any]].map { s =>
              s._1.replace(key.concat("."), "") -> s._2
            }
            (key -> tf(b))
        }
        obj
      }.foldLeft(JObject(Nil))(_ ~ _)
    }.reduceLeft(_ ~ _)
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
      ("mean" -> snapshot.getMean) ~
      ("median" -> snapshot.getMedian) ~
      ("std_dev" -> snapshot.getStdDev) ~
      ("p75" -> snapshot.get75thPercentile) ~
      ("p95" -> snapshot.get95thPercentile) ~
      ("p98" -> snapshot.get98thPercentile) ~
      ("p99" -> snapshot.get99thPercentile) ~
      ("p999" -> snapshot.get999thPercentile)
  }

  private def writeMeteredFields(metric: Metered): JObject = {
    ("unit" -> "events/second") ~
      ("count" -> metric.getCount) ~
      ("mean" -> metric.getMeanRate) ~
      ("m1" -> metric.getOneMinuteRate) ~
      ("m5" -> metric.getFiveMinuteRate) ~
      ("m15", metric.getFifteenMinuteRate)
  }

  private def checkNan(num: AnyVal): JValue = {
    num match {
      case Double.NaN => "Nan"
      case _ => matchAny(num)
    }
  }

  private def matchAny(num: Any): JValue = {
    try {
      (num: Any) match {
        case z: Boolean => boolean2jvalue(z)
        case b: Byte => int2jvalue(b.toInt)
        case c: Char => int2jvalue(c.toInt)
        case s: Short => int2jvalue(s.toInt)
        case i: Int => int2jvalue(i)
        case j: Long => long2jvalue(j)
        case f: Float => float2jvalue(f)
        case d: Double => bigdecimal2jvalue(d)
        case st: String => string2jvalue(st)
        case r: AnyRef => JsonAST.JNull
      }
    } catch {
      case e: Throwable =>
        string2jvalue("Error evaluating the value: " + e.getMessage)
    }
  }
}
