package com.oracle.infy.wookiee.utils

import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent._
import scala.concurrent.ExecutionContext

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
}
