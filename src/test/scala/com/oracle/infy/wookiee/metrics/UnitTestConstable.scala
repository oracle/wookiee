package com.oracle.infy.wookiee.metrics

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.metrics.tests.{MetricsServiceTest, MetricsTest}

import scala.concurrent.ExecutionContext

object UnitTestConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    exitNegativeOnFailure(
      runTestsAsync(
        List(
          (MetricsTest.tests(), "UnitTest - metrics test"),
          (MetricsServiceTest.tests(), "UnitTest - service test")
        )
      )
    )
  }

}
