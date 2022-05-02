package com.oracle.infy.wookiee.health

import cats.effect.unsafe.implicits.global
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.health.tests.HealthRoutesTest

import scala.concurrent.ExecutionContext

object UnitTestConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)

    exitNegativeOnFailure(
      runTestsAsync(
        List(
          (HealthRoutesTest.tests(), "UnitTest - health routes test")
        )
      )
    )
  }

}
