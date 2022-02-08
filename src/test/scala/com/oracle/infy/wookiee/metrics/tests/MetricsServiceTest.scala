package com.oracle.infy.wookiee.metrics.tests

import cats.effect.IO
import com.oracle.infy.wookiee.functional.metrics.WookieeMetricsService
import com.oracle.infy.wookiee.functional.metrics.core.WookieeMetricsReporter
import com.oracle.infy.wookiee.grpc.common.UTestScalaCheck
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry
import com.oracle.infy.wookiee.grpc.utils.implicits._
import utest.{Tests, test}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

object MetricsServiceTest extends UTestScalaCheck {

  def tests()(implicit executionContext: ExecutionContext): Tests = {

    val testReporter = (_: WookieeRegistry) =>
      IO(new WookieeMetricsReporter[IO]() {
        override def report(): IO[Unit] = IO.unit
        override def stop(): IO[Unit] = IO.unit
      })

    val checkMetricsService = {
      WookieeMetricsService
        .register(testReporter)
        .use(
          metrics =>
            for {
              _ <- metrics.time("timerTest")(IO("bar"))
              t <- metrics.timer("timerTest")
              _ <- t.update(1000, TimeUnit.NANOSECONDS)
              c1 <- metrics.counter("counterTest")
              _ <- c1.inc(2)
              m <- metrics.meter("meterTest")
              _ <- m.mark()
              h <- metrics.histogram("histogramTest", biased = true)
              _ <- h.update(1)
              json <- metrics.getMetrics
            } yield {
              val isJvmMetricsExists = json.hcursor.downField("system").downField("jvm").succeeded
              val metricsJson = json.hcursor.downField("system").downField("metrics")
              val timerCount = metricsJson.downField("timerTest").downField("rate").downField("count").as[Int]
              val counterCount = metricsJson.downField("counterTest").as[Int]
              val meterCount = metricsJson.downField("meterTest").downField("count").as[Int]
              val histogramCount = metricsJson.downField("histogramTest").downField("count").as[Int]
              isJvmMetricsExists && timerCount === Right(2) && counterCount === Right(2) && meterCount === Right(1) &&
              histogramCount === Right(1)
            }
        )
    }.unsafeToFuture()

    Tests {
      test("register metrics and get correct counts") {
        checkMetricsService.map(assert)
      }
    }
  }
}
