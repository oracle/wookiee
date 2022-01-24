package com.oracle.infy.wookiee

import cats.effect.{ExitCode, IO, IOApp}
import com.codahale.metrics.ConsoleReporter

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

object MetricsExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    // This is reporter implementation to dump metrics data into console
    val consoleReporter = (wookieeRegistry: WookieeRegistry) =>
      IO(new WookieeMetricsReporter[IO]() {
        val consoleReport: ConsoleReporter = ConsoleReporter
          .forRegistry(wookieeRegistry.metricRegistry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
        def report(): IO[Unit] = IO(consoleReport.start(3, TimeUnit.SECONDS))
        def stop(): IO[Unit] = IO(consoleReport.stop())
      })

    def foo(msg: String): IO[String] =
      for {
        _ <- IO.sleep(3.seconds)
      } yield msg

    // Register metric service with a reporter(graphite/sl4J/JMX/..) to pump the metrics data into target consumer
    WookieeMetricsService
      .register(consoleReporter)
      .use(
        metrics =>
          for {
            t <- metrics.timer("timer")
            _ <- t.update(1000, TimeUnit.NANOSECONDS)
            _ <- metrics.time("timer")(foo("bar"))
            c <- metrics.counter("counter")
            _ <- c.inc(2)
            m <- metrics.meter("meter")
            _ <- m.mark()
            h <- metrics.histogram("histogram", biased = true)
            _ <- h.update(1)
            _ <- IO.sleep(10.seconds)
            //This is to get metrics data in Json format
            _ <- metrics.getMetrics.map(println)
          } yield ExitCode.Success
      )
  }
}
