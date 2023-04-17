package com.oracle.infy.wookiee.utils

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.unsafe.implicits.global
import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent._
import scala.concurrent.{ExecutionContext, Future}

object ThreadUtil extends LoggingAdapter {

  // Be sure threadNames is unique or it could lead to deadlocks
  // If parallelism = None then we'll use a cached thread pool with no size limit, if Some(1) we'll use a single thread pool
  def createEC(threadNames: String): ExecutionContext = createEC(threadNames, None)

  def createEC(
      threadNames: String,
      parallelism: Option[Int]
  ): ExecutionContext = {
    ExecutionContext
      .fromExecutor(parallelism match {
        case Some(1) =>
          Executors.newSingleThreadExecutor(
            threadFactory(s"$threadNames-singular")
          )
        case Some(threads) =>
          new ForkJoinPool(
            threads,
            forkJoinWorkerThreadFactoryFactory(s"$threadNames-multi"),
            uncaughtExceptionHandler,
            false
          )
        case None =>
          Executors.newCachedThreadPool(threadFactory(s"$threadNames-cached"))
      })
  }

  def ioRuntime(mainEC: ExecutionContext, prefix: String): IORuntime = {
    val blockingEC = createEC(s"$prefix-blocking")
    ioRuntime(mainEC, blockingEC, prefix)
  }

  def ioRuntime(mainEC: ExecutionContext, blockingEC: ExecutionContext, prefix: String): IORuntime = {
    val scheduled = scheduledThreadPoolExecutor(prefix, 5)
    ioRuntime(mainEC, blockingEC, scheduled)
  }

  def ioRuntime(
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      scheduled: ScheduledThreadPoolExecutor
  ): IORuntime =
    IORuntime(mainEC, blockingEC, Scheduler.fromScheduledExecutor(scheduled), () => (), IORuntimeConfig())

  def scheduledThreadPoolExecutor(
      prefix: String,
      threads: Int
  ): ScheduledThreadPoolExecutor = {
    val tp = new ScheduledThreadPoolExecutor(
      threads,
      threadFactory(s"$prefix-scheduled")
    )
    tp.setRemoveOnCancelPolicy(true)
    tp
  }

  def dispatcherIO(): Dispatcher[IO] = new Dispatcher[IO] {

    override def unsafeToFutureCancelable[A](fa: IO[A]): (Future[A], () => Future[Unit]) =
      fa.unsafeToFutureCancelable()
  }

  def uncaughtExceptionHandler: UncaughtExceptionHandler = new UncaughtExceptionHandler {

    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      log.error(s"TU400: Got an uncaught exception $e on thread ${t.getName}", e)
    }
  }

  def forkJoinWorkerThreadFactoryFactory(prefix: String): ForkJoinWorkerThreadFactory =
    new ForkJoinWorkerThreadFactory {

      override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
        val worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
        worker.setName(s"$prefix-${worker.getPoolIndex}")
        worker
      }
    }

  def threadFactory(prefix: String): ThreadFactory = new ThreadFactory {

    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setName(s"$prefix-${t.getId.toString}")
      t.setUncaughtExceptionHandler(uncaughtExceptionHandler)
      t.setDaemon(true)
      t
    }
  }

  // A helper method to wait for a result to be Some(..), great for testing
  def awaitResult[T](
      f: => Option[T],
      waitMs: Long = 15000L, // scalafix:ok
      ignoreError: Boolean = true // scalafix:ok
  ): T = {
    val goUntil = System.currentTimeMillis + waitMs
    while (System.currentTimeMillis < goUntil) {
      try {
        f match {
          case Some(result) =>
            return result // scalafix:ok
          case None =>
            Thread.sleep(500L) // scalafix:ok
        }
      } catch {
        case e: Exception =>
          if (ignoreError && System.currentTimeMillis < goUntil) {
            Thread.sleep(500L) // scalafix:ok
          } else {
            throw e // scalafix:ok
          }
      }
    }

    throw new RuntimeException("Timed out waiting for result") // scalafix:ok
  }
}
