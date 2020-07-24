package com.oracle.infy.wookiee.grpc.common

import org.scalacheck.util.Pretty
import org.scalacheck.{Prop, Test}
import utest._

trait UTestScalaCheck {

  protected[grpc] object UTestReporter extends Test.TestCallback {
    private val prettyParams = Pretty.defaultParams

    override def onTestResult(name: String, res: org.scalacheck.Test.Result): Unit = {
      val scalaCheckResult = if (res.passed) "" else Pretty.pretty(res, prettyParams)
      assert(scalaCheckResult.isEmpty)
    }
  }

  implicit protected[grpc] class PropWrapper(prop: Prop) {

    def checkUTest(): Unit = {
      prop.check(
        Test
          .Parameters
          .default
          .withTestCallback(UTestReporter)
          .withMinSuccessfulTests(100)
      )
    }
  }

}
