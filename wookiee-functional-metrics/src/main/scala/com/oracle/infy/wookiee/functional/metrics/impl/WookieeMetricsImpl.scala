package com.oracle.infy.wookiee.functional.metrics.impl

import cats.effect.{IO, Resource}
import com.codahale.metrics.{
  ExponentiallyDecayingReservoir,
  Metric,
  MetricFilter,
  MetricRegistry,
  UniformReservoir,
  DefaultSettableGauge => DWDefaultSettableGauge,
  Histogram => DWHistogram
}
import com.oracle.infy.wookiee.functional.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.functional.metrics.json.Serde
import com.oracle.infy.wookiee.metrics.model._
import io.circe.Json
import io.circe.syntax._

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.jdk.CollectionConverters._

final class WookieeMetricsImpl(registry: WookieeRegistry) extends WookieeMetrics[IO] with Serde {

  override def timer(name: String): IO[Timer[IO]] =
    for {
      timer <- IO(registry.metricRegistry.timer(name))
    } yield new Timer[IO] {

      override def time[A](f: IO[A]): IO[A] =
        Resource
          .make(IO(timer.time()))(
            c =>
              IO {
                c.stop()
                ()
              }
          )
          .use { _ =>
            f
          }

      override def update(time: Long, unit: TimeUnit): IO[Unit] = IO(timer.update(time, unit))
    }

  override def time[A](name: String)(inner: IO[A]): IO[A] = timer(name).flatMap(_.time(inner))

  override def counter(name: String): IO[Counter[IO]] =
    for {
      counter <- IO(registry.metricRegistry.counter(name))
    } yield new Counter[IO] {
      override def inc(): IO[Unit] = IO(counter.inc())
      override def inc(amount: Long): IO[Unit] = IO(counter.inc(amount))
    }

  override def meter(name: String): IO[Meter[IO]] =
    for {
      meter <- IO(registry.metricRegistry.meter(name))
    } yield new Meter[IO] {
      override def mark(): IO[Unit] = IO(meter.mark())
      override def mark(amount: Long): IO[Unit] = IO(meter.mark(amount))
    }

  override def histogram(name: String, biased: Boolean): IO[Histogram[IO]] =
    for {
      histogram <- IO(
        registry.metricRegistry.getHistograms(MetricFilter.startsWith(name)).values().asScala.headOption match {
          case Some(h) => h
          case None =>
            if (biased) {
              registry.metricRegistry.register(name, new DWHistogram(new ExponentiallyDecayingReservoir()))
            } else {
              registry.metricRegistry.register(name, new DWHistogram(new UniformReservoir()))
            }
        }
      )
    } yield new Histogram[IO] {
      override def update(amount: Long): IO[Unit] = IO(histogram.update(amount))
    }

  override def gauge[A](name: String): IO[Gauge[IO, A]] =
    for {
      settableGauge <- IO(registry.metricRegistry.register(name, new DWDefaultSettableGauge[A]))
    } yield new Gauge[IO, A] {
      override def setValue(value: A): IO[Unit] = IO(settableGauge.setValue(value))
    }

  override def remove(name: String): IO[Boolean] = IO(registry.metricRegistry.remove(name))

  def getMetrics: IO[Json] =
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

  private def processJvmRegistry(jvmRegistry: MetricRegistry): Map[String, mutable.Map[String, Metric]] =
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
