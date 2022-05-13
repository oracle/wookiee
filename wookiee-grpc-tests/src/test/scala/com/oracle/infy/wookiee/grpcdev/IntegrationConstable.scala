package com.oracle.infy.wookiee.grpcdev

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpcdev.tests.SrcGenIntegrationTest

import scala.concurrent.ExecutionContext

object IntegrationConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)

    exitNegativeOnFailure(
      Dispatcher[IO]
        .use { d =>
          implicit val dispatcher: Dispatcher[IO] = d
          IO {
            runTestsAsync(
              List(
                (SrcGenIntegrationTest.tests(), "IntegrationTest - SrcGen")
              )
            )
          }
        }
        .unsafeRunSync()
    )
  }
}
