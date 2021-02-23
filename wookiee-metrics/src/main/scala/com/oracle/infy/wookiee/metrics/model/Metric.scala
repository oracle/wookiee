package com.oracle.infy.wookiee.metrics.model

import java.util.concurrent.TimeUnit

import cats.effect.IO
import com.codahale.metrics.{
  Counter => DWCounter,
  Histogram => DWHistogram,
  Meter => DWMeter,
  Timer => DWTimer,
  Gauge => DWGauge,
  _
}

import scala.jdk.CollectionConverters._

sealed trait Metric {}

case class Timer(timer: IO[DWTimer]) extends Metric {

  def time[A]()(f: IO[A]): IO[A] =
    for {
      c <- timer.map(_.time())
      result <- f
      _ <- IO(c.stop())
    } yield result

  def update(time: Long, unit: TimeUnit): IO[Unit] = timer.map(_.update(time, unit))
}

object Timer {

  def apply(name: String, registry: MetricRegistry): IO[Timer] = {
    IO.pure(
      Timer(IO.delay(registry.timer(name)))
    )
  }
}

case class Counter(counter: IO[DWCounter]) extends Metric {

  def inc(): IO[Unit] = counter.map(_.inc())

  def inc(amount: Double): IO[Unit] = counter.map(_.inc(amount.toLong))
}

object Counter {

  def apply(name: String, registry: MetricRegistry): IO[Counter] = {
    IO.pure(Counter(IO.delay(registry.counter(name))))
  }
}

case class Meter(meter: IO[DWMeter]) extends Metric {

  def mark(): IO[Unit] = meter.map(_.mark())

  def mark(amount: Long): IO[Unit] = meter.map(_.mark(amount))

  def mark[A]()(f: IO[A]): IO[A] = {
    for {
      result <- f
      _ <- meter.map(_.mark())
    } yield result
  }
}

object Meter {

  def apply(name: String, registry: MetricRegistry): IO[Meter] = {
    IO.pure(Meter(IO.delay(registry.meter(name))))
  }
}

case class Histogram(histogram: IO[DWHistogram]) extends Metric {
  def update(amount: Double): IO[Unit] = histogram.map(_.update(amount.toLong))

}

object Histogram {

  def apply(name: String, registry: MetricRegistry, biased: Boolean): IO[Histogram] = {

    val histogram = IO.delay(registry.getHistograms(MetricFilter.startsWith(name)).values().asScala.headOption match {
      case Some(h) => h
      case None =>
        if (biased) {
          new DWHistogram(new ExponentiallyDecayingReservoir())
        } else {
          new DWHistogram(new UniformReservoir())
        }
    })
    IO.pure(Histogram(histogram))
  }
}

case class Gauge[A](dwGauge: IO[DWGauge[A]]) extends Metric {
  def getValue: IO[A] = dwGauge.map(_.getValue())
}

object Gauge {

  def apply[A](name: String, registry: MetricRegistry, f: => A): IO[Gauge[A]] = {
    val gauge: IO[DWGauge[A]] =
      IO.delay(registry.register(name, new DWGauge[A]() {
        override def getValue: A = f
      }))
    IO.pure(Gauge[A](gauge))

  }
}
