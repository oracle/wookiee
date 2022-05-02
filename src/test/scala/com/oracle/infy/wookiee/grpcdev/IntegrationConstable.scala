package com.oracle.infy.wookiee.grpcdev

import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpcdev.tests.SrcGenIntegrationTest

import scala.concurrent.ExecutionContext

object IntegrationConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)

    exitNegativeOnFailure(
      runTestsAsync(
        List(
          (SrcGenIntegrationTest.tests(), "IntegrationTest - SrcGen")
        )
      )
    )
  }
}
