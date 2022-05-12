package com.oracle.infy.wookiee.grpc.common

import cats.data.EitherT
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import org.scalacheck.Prop
import utest.framework.{Formatter, HTree, Result}
import utest.ufansi.Str
import utest.{TestRunner, Tests, ufansi}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait ConstableCommon {

  private def uncaughtExceptionHandler: UncaughtExceptionHandler = (t: Thread, e: Throwable) => {
    println(s"Got an uncaught exception on thread: $e" ++ t.getName)
  }

  private def blockingThreadFactory(prefix: String): ThreadFactory = (r: Runnable) => {
    val t = new Thread(r)
    t.setUncaughtExceptionHandler(uncaughtExceptionHandler)
    t.setName(s"$prefix-blocking-${t.getId.toString}")
    t.setDaemon(true)
    t
  }

  def mainExecutionContext(parallelism: Int): ExecutionContext =
    ExecutionContext.fromExecutor(
      new ForkJoinPool(
        parallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        uncaughtExceptionHandler,
        true
      )
    )

  def blockingExecutionContext(prefix: String): ExecutionContext =
    ExecutionContext.fromExecutorService(
      Executors.newCachedThreadPool(
        blockingThreadFactory(prefix)
      )
    )

  implicit def eitherTListenerErrorToProp(
      implicit runtime: IORuntime
  ): EitherT[IO, WookieeGrpcError, Boolean] => Prop = { e =>
    val result = e
      .value
      .unsafeRunSync()
      .left
      .map(err => {
        println(err)
        false
      })
      .merge
    Prop(result)
  }

  private def testFormatter =
    new Formatter {

      override def formatIcon(success: Boolean): Str =
        formatResultColor(success)(
          if (success) "✅💯✅" else "\uD83E\uDD26\u200D\uD83E\uDD26\u200D\uD83E\uDD26\u200D️"
        )
    }

  def exitNegativeOnFailure(results: List[HTree[String, Result]]): Unit = {
    val existsFailure = results.exists { htree: HTree[String, Result] =>
      htree.leaves.map(result => result.value.isFailure).exists(identity)
    }

    if (existsFailure) {
      System.exit(-1)
    }
    ()
  }

  def runTests(
      tests: List[(Tests, String)]
  ): List[HTree[String, Result]] =
    tests.map {
      case (test, label) =>
        TestRunner
          .runAndPrint(
            test,
            label,
            formatter = testFormatter
          )
    }

  def runTestsAsync(
      tests: List[(Tests, String)]
  )(implicit ec: ExecutionContext, dispatcher: Dispatcher[IO]): List[HTree[String, Result]] =
    dispatcher.unsafeRunSync {
      IO.fromFuture {
        IO {
          Future
            .sequence(
              tests.map {
                case (test, label) =>
                  TestRunner
                    .runAndPrintAsync(
                      test,
                      label,
                      formatter = testFormatter
                    )
              }
            )
            .map { results =>
              formatResults(results)
              results
            }

        }
      }
    }

  private def formatResults(results: Seq[HTree[String, Result]]): Unit =
    results.flatMap(tree => tree.leaves.map(_.value.isFailure)).find(identity).foreach { _ =>
      println()
      println(ufansi.Color.Red("T E S T S  F A I L E D"))
      println("\"Laws change, depending on who's making them, but justice is justice.\" - Constable Odo")
    }

  implicit def sleep: FiniteDuration => IO[Unit] = { duration: FiniteDuration =>
    IO.sleep(duration)
  }
}
