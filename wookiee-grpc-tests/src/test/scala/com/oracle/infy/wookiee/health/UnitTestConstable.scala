package com.oracle.infy.wookiee.health

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.health.tests.HealthRoutesTest

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
                (HealthRoutesTest.tests(), "UnitTest - health routes test")
              )
            )
          }
        }
        .unsafeRunSync()
    )
  }

}
