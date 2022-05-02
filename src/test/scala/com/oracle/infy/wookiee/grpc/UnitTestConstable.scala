package com.oracle.infy.wookiee.grpc

import cats.effect.{Deferred, IO}
import cats.effect.std.Queue
import cats.implicits._
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, MockHostNameService, WookieeGrpcHostListener}
import com.oracle.infy.wookiee.grpc.model.Host
import com.oracle.infy.wookiee.grpc.tests.{GrpcListenerTest, SerdeTest}
import fs2.Stream
import org.typelevel.log4cats.noop.NoOpLogger

import scala.concurrent.ExecutionContext

object UnitTestConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = mainExecutionContext(4)

    def pushMessagesFuncAndListenerFactory(
        callback: Set[Host] => IO[Unit]
    ): IO[(Set[Host] => IO[Unit], () => IO[Unit], ListenerContract[IO, Stream])] =
      for {
        logger <- NoOpLogger.impl[IO].pure[IO]
        queue <- Queue.unbounded[IO, Set[Host]]
        killswitch <- Deferred[IO, Either[Throwable, Unit]]

      } yield {
        val pushMessagesFunc = { hosts: Set[Host] =>
          queue.offer(hosts)
        }
        val listener: ListenerContract[IO, Stream] =
          new WookieeGrpcHostListener(
            callback,
            new MockHostNameService(Fs2CloseableImpl(Stream.repeatEval(queue.take), killswitch)),
            discoveryPath = ""
          )(logger)

        val cleanup: () => IO[Unit] = () => {
          IO(())
        }

        (pushMessagesFunc, cleanup, listener)
      }

    val grpcTests = GrpcListenerTest.tests(100, pushMessagesFuncAndListenerFactory)

    exitNegativeOnFailure(
      runTestsAsync(
        List(
          (SerdeTest.tests, "UnitTest - Serde"),
          (grpcTests, "UnitTest - GRPC Tests")
        )
      )
    )
  }

}
