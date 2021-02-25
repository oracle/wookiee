package com.oracle.infy.wookiee.metrics.impl

import cats.effect.IO
import com.codahale.metrics.{Metric, MetricRegistry}
import com.oracle.infy.wookiee.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.metrics.json.Serde
import com.oracle.infy.wookiee.metrics.model._
import io.circe.Json
import io.circe.syntax._

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class WookieeMetricsImpl(registry: WookieeRegistry) extends WookieeMetrics[IO] with Serde {

  override def timer(name: String): IO[Timer] = Timer(name, registry.metricRegistry)

  override def counter(name: String): IO[Counter] = Counter(name, registry.metricRegistry)

  override def meter(name: String): IO[Meter] = Meter(name, registry.metricRegistry)

  override def histogram(name: String, biased: Boolean): IO[Histogram] =
    Histogram(name, registry.metricRegistry, biased)

  override def gauge[A](name: String, f: () => A): IO[Gauge[A]] = Gauge(name, registry.metricRegistry, f)

  override def time[A](name: String)(inner: IO[A]): IO[A] =
    for {
      timer <- timer(name)
      result <- timer.time()(inner)
    } yield result

  override def remove(name: String): IO[Boolean] = IO(registry.metricRegistry.remove(name))

  def getMetrics: IO[Json] = {
    IO(
      Json
        .obj(
          (
            "system",
            Json.obj(
              ("jvm", processJvmRegistry(registry.jvmRegistry).asJson),
              ("metrics", registry.metricRegistry.getMetrics.asScala.asJson)
            )
          )
        )
    )
  }

  private def processJvmRegistry(jvmRegistry: MetricRegistry): Map[String, mutable.Map[String, Metric]] = {
    jvmRegistry
      .getMetrics
      .asScala
      .groupBy(
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
      )
  }

}
