package com.oracle.infy.wookiee.metrics.json

import com.codahale.metrics._
import io.circe.{Encoder, _}
import io.circe.syntax._

trait Serde {

  implicit def timerEncoder: Encoder[Timer] =
    Encoder.instance(
      metric =>
        Json
          .obj(("duration", Json.obj(("unit", Json.fromString("nanoseconds"))).deepMerge(sampling(metric))))
          .deepMerge(Json.obj(("rate", meteredFields(metric))))
    )

  implicit def meterEncoder: Encoder[Meter] =
    Encoder.instance(
      metric => Json.obj(("event_type", Json.fromLong(metric.getCount))).deepMerge(meteredFields(metric))
    )

  implicit def counterEncoder: Encoder[Counter] = Encoder.instance(metric => Json.fromLong(metric.getCount))

  implicit def histogramEncoder: Encoder[Histogram] =
    Encoder.instance(metric => Json.obj(("count", Json.fromLong(metric.getCount))).deepMerge(sampling(metric)))

  implicit def gaugeEncoder[A]: Encoder[Gauge[A]] =
    Encoder.instance({ metric: Gauge[A] =>
      metric.getValue match {
        case v: Byte    => Json.fromInt(v.toInt)
        case v: Char    => Json.fromInt(v.toInt)
        case v: Short   => Json.fromInt(v)
        case v: Int     => Json.fromInt(v)
        case v: Long    => Json.fromLong(v)
        case v: Double  => Json.fromDoubleOrNull(v)
        case v: Float   => Json.fromFloatOrNull(v)
        case v: Boolean => Json.fromBoolean(v)
        case _          => Json.Null
      }
    })

  implicit def metricsEncoder[A]: Encoder[Metric] =
    Encoder.instance {
      case t: Timer     => t.asJson
      case c: Counter   => c.asJson
      case m: Meter     => m.asJson
      case h: Histogram => h.asJson
      case g: Gauge[_]  => g.asJson
    }

  def meteredFields(metric: Metered): Json =
    Json.obj(
      ("unit", Json.fromString("events/second")),
      ("count", Json.fromLong(metric.getCount)),
      ("mean", Json.fromDoubleOrNull(metric.getMeanRate)),
      ("m1", Json.fromDoubleOrNull(metric.getOneMinuteRate)),
      ("m5", Json.fromDoubleOrNull(metric.getFiveMinuteRate)),
      ("m15", Json.fromDoubleOrNull(metric.getFifteenMinuteRate))
    )

  def sampling(metric: Sampling): Json = {
    val snapshot = metric.getSnapshot
    Json.obj(
      ("min", Json.fromLong(snapshot.getMin)),
      ("max", Json.fromLong(snapshot.getMax)),
      ("mean", Json.fromDoubleOrNull(snapshot.getMean))
    )
  }

}
