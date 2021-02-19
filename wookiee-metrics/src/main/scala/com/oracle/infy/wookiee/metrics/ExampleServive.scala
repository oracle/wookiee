package com.oracle.infy.wookiee.metrics

import java.util.concurrent.TimeUnit

import cats.effect.{ExitCode, IO, IOApp}
import com.oracle.infy.wookiee.metrics.contract.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model.MetricsSettings

import scala.concurrent.duration._

object ExampleServive extends IOApp {

  def foo(x: String): IO[String] = {
    for {
      _ <- IO.sleep(3.seconds)
    } yield x
  }

  override def run(args: List[String]): IO[ExitCode] = {

    val settings = MetricsSettings(
      applicationName = "WookieeMetrics",
      metricPrefix = "wookiee.metrics",
      jmxEnabled = false,
      graphiteEnabled = false,
      graphiteHost = "",
      graphitePort = 1,
      graphiteInterval = 10
    )

    val metrics: WookieeMetrics[IO] = WookieeMetricsService.register(settings)
    for {
      _ <- metrics.startReports()
      t1 <- metrics.timer("timertest")
      _ <- t1.time()(foo("Test"))
      c1 <- metrics.counter("countertest")
      _ <- c1.inc(2)
      _ <- IO.sleep(10.seconds)
      _ <- metrics.timer("timertest").flatMap(_.update(1000, TimeUnit.NANOSECONDS))
      _ <- metrics.meter("metertest").flatMap(_.mark())
      _ <- IO.sleep(10.seconds)
    } yield ExitCode.Success
  }
}
