package com.oracle.infy.wookiee.metrics.impl

import java.util.concurrent.TimeUnit

import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.oracle.infy.wookiee.metrics.contract.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model.{Counter, Histogram, Meter, Timer}

import scala.concurrent.duration.TimeUnit

class WookieeMetricsNoOpImpl extends WookieeMetrics[IO] {

  val registry: MetricRegistry = new MetricRegistry()

  override def timer(name: String): IO[Timer] =
    IO(new Timer(IO(registry.timer(name))) {

      override def time[A]()(f: IO[A]): IO[A] = {
        for {
          result <- f
        } yield result
      }
      override def update(time: Long, unit: TimeUnit = TimeUnit.NANOSECONDS): IO[Unit] = IO.unit
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

  override def remove(name: String): IO[Boolean] = IO.pure(true)
  override def startReports(): IO[Unit] = IO.unit

  override def stopReports(): IO[Unit] = IO.unit

}
