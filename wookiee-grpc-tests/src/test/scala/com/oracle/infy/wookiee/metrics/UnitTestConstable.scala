package com.oracle.infy.wookiee.metrics

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.metrics.tests.{MetricsServiceTest, MetricsTest}

import scala.concurrent.ExecutionContext

object UnitTestConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)

    exitNegativeOnFailure(
      Dispatcher
        .parallel[IO]
        .use { d =>
          implicit val dispatcher: Dispatcher[IO] = d
          IO {
            runTestsAsync(
              List(
                (MetricsTest.tests(), "UnitTest - metrics test"),
                (MetricsServiceTest.tests(), "UnitTest - service test")
              )
            )
          }
        }
        .unsafeRunSync()
    )
  }

}
