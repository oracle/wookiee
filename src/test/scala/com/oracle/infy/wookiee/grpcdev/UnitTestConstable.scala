package com.oracle.infy.wookiee.grpcdev

import cats.effect.{ContextShift, IO}
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpcdev.tests.{GrpcDevTest /*, SrcGenTest*/}

import scala.concurrent.ExecutionContext

object UnitTestConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    exitNegativeOnFailure(
      runTestsAsync(
        List(
          (GrpcDevTest.tests(), "UnitTest - SrcGen") /*,
          (SrcGenTest.tests(), "UnitTest - SrcGenTwo")*/
        )
      )
    )
  }
}
