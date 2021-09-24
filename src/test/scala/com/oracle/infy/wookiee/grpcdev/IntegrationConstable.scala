//package com.oracle.infy.wookiee.grpcdev
//
//import cats.effect.{ContextShift, IO}
//import com.oracle.infy.wookiee.grpc.common.ConstableCommon
//import com.oracle.infy.wookiee.grpcdev.tests.{GrpcDevTest, SrcGenIntegrationTest}
//
//import scala.concurrent.ExecutionContext
//
//object IntegrationConstable extends ConstableCommon {
//
//  def main(args: Array[String]): Unit = {
//    implicit val ec: ExecutionContext = mainExecutionContext(4)
//    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
//
//    exitNegativeOnFailure(
//      runTestsAsync(
//        List(
//          (SrcGenIntegrationTest.tests(), "IntegrationTest - SrcGen")
//        )
//      )
//    )
//  }
//}
