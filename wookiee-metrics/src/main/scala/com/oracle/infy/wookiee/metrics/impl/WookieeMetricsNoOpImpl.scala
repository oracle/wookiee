package com.oracle.infy.wookiee.metrics.impl

import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.oracle.infy.wookiee.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model.{Counter, Gauge, Histogram, Meter, Timer}
import com.codahale.metrics.{Gauge => DWGauge}
import io.circe.Json

import scala.concurrent.duration.TimeUnit

class WookieeMetricsNoOpImpl(registry: MetricRegistry) extends WookieeMetrics[IO] {

  override def timer(name: String): IO[Timer] =
    IO(new Timer(IO(registry.timer(name))) {

      override def time[A]()(f: IO[A]): IO[A] = {
        for {
          result <- f
        } yield result
      }
      override def update(time: Long, unit: TimeUnit): IO[Unit] = IO.unit
    })

  override def counter(name: String): IO[Counter] =
    IO(new Counter(IO(registry.counter(name))) {
      override def inc(): IO[Unit] = IO.unit

      override def inc(amount: Double): IO[Unit] = IO.unit

    })

  override def meter(name: String): IO[Meter] =
    IO(new Meter(IO(registry.meter(name))) {
      override def mark(): IO[Unit] = IO.unit

      override def mark(amount: Long): IO[Unit] = IO.unit

      override def mark[A]()(f: IO[A]): IO[A] = {
        for {
          result <- f
        } yield result
      }
    })

  override def histogram(name: String, biased: Boolean): IO[Histogram] =
    IO(new Histogram(IO(registry.histogram(name))) {
      override def update(amount: Double): IO[Unit] = IO.unit
    })

  override def gauge[A](name: String, f: => A): IO[Gauge[A]] =
    IO(new Gauge[A](IO(body = new DWGauge[A]() {
      override def getValue: A = f
    })))

  override def remove(name: String): IO[Boolean] = IO.pure(true)

  override def stopReports(): IO[Unit] = IO.unit

  def getMetrics: IO[Json] = IO(Json.Null)

}
