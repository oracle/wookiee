package com.oracle.infy.wookiee.functional.metrics.impl

import cats.effect.IO
import com.codahale.metrics.{
  UniformReservoir,
  Counter => DWCounter,
  Gauge => DWGauge,
  Histogram => DWHistogram,
  Meter => DWMeter,
  Timer => DWTimer
}
import com.oracle.infy.wookiee.functional.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model._
import io.circe.Json

import scala.concurrent.duration.TimeUnit

class WookieeMetricsNoOpImpl() extends WookieeMetrics[IO] {

  override def timer(name: String): IO[Timer] =
    IO(new Timer(IO(new DWTimer())) {

      override def time[A]()(f: IO[A]): IO[A] =
        for {
          result <- f
        } yield result
      override def update(time: Long, unit: TimeUnit): IO[Unit] = IO.unit
    })

  override def counter(name: String): IO[Counter] =
    IO(new Counter(IO(new DWCounter())) {
      override def inc(): IO[Unit] = IO.unit

      override def inc(amount: Double): IO[Unit] = IO.unit

    })

  override def meter(name: String): IO[Meter] =
    IO(new Meter(IO(new DWMeter())) {
      override def mark(): IO[Unit] = IO.unit

      override def mark(amount: Long): IO[Unit] = IO.unit

      override def markFunc[A]()(f: IO[A]): IO[A] =
        for {
          result <- f
        } yield result
    })

  override def histogram(name: String, biased: Boolean): IO[Histogram] =
    IO(new Histogram(IO(new DWHistogram(new UniformReservoir()))) {
      override def update(amount: Long): IO[Unit] = IO.unit
    })

  override def gauge[A](name: String, f: () => A): IO[Gauge[A]] =
    IO(new Gauge[A](IO(new DWGauge[A]() {
      override def getValue: A = f()
    })))

  override def time[A](name: String)(inner: IO[A]): IO[A] =
    for {
      result <- inner
    } yield result

  override def remove(name: String): IO[Boolean] = IO(true)

  def getMetrics: IO[Json] = IO(Json.Null)

}
