package com.oracle.infy.wookiee.functional.metrics.impl

import cats.effect.IO
import com.oracle.infy.wookiee.functional.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model._
import io.circe.Json

import java.util.concurrent.TimeUnit

final class WookieeMetricsNoOpImpl extends WookieeMetrics[IO] {

  override def timer(name: String): IO[Timer[IO]] = IO {
    new Timer[IO] {
      override def time[A](f: IO[A]): IO[A] = f

      override def update(time: Long, unit: TimeUnit): IO[Unit] = IO.unit
    }
  }

  override def time[A](name: String)(inner: IO[A]): IO[A] = inner

  override def counter(name: String): IO[Counter[IO]] = IO {
    new Counter[IO] {
      override def inc(): IO[Unit] = IO.unit
      override def inc(amount: Long): IO[Unit] = IO.unit
    }
  }

  override def meter(name: String): IO[Meter[IO]] = IO {
    new Meter[IO] {
      override def mark(): IO[Unit] = IO.unit

      override def mark(amount: Long): IO[Unit] = IO.unit
    }
  }

  override def histogram(name: String, biased: Boolean): IO[Histogram[IO]] = IO { (amount: Long) =>
    {
      val _ = amount
      IO.unit
    }
  }

  override def gauge[A](name: String): IO[Gauge[IO, A]] = IO { (value: A) =>
    val _ = value
    IO.unit
  }

  override def remove(name: String): IO[Boolean] = IO(false)

  override def getMetrics: IO[Json] = IO(Json.Null)
}
