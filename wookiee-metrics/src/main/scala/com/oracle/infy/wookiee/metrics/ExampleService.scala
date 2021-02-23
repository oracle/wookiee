package com.oracle.infy.wookiee.metrics

import java.util.concurrent.TimeUnit

import cats.effect.{ExitCode, IO, IOApp}
import com.codahale.metrics.ConsoleReporter
import com.oracle.infy.wookiee.metrics.core.WookieeMetricsReporter
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry
import scala.concurrent.duration._

object ExampleService extends IOApp {

  def foo(x: String): IO[String] = for {
      _ <- IO.sleep(3.seconds)
    } yield x

  override def run(args: List[String]): IO[ExitCode] = {

    def consoleReport(wookieeRegistry: WookieeRegistry): IO[WookieeMetricsReporter[IO]] =
      IO(new WookieeMetricsReporter[IO]() {
        val consoleReport: ConsoleReporter = ConsoleReporter
          .forRegistry(wookieeRegistry.metricRegistry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
        def start(): IO[Unit] = IO.delay(consoleReport.start(3, TimeUnit.SECONDS))

        def stop(): IO[Unit] = IO.delay(consoleReport.stop())
      })
    for {
      metrics <- WookieeMetricsService.register(consoleReport)
      t1 <- metrics.timer("timertest")
      _ <- t1.time()(foo("Test"))
      _ <- metrics.getMetrics.map(println)
      c1 <- metrics.counter("countertest")
      _ <- c1.inc(2)
      _ <- IO.sleep(10.seconds)
      _ <- metrics.timer("timertest1").flatMap(_.update(1000, TimeUnit.NANOSECONDS))
      _ <- metrics.meter("metertest").flatMap(_.mark())
      _ <- IO.sleep(10.seconds)
      _ <- metrics.getMetrics.map(println)
    } yield ExitCode.Success
  }
}
