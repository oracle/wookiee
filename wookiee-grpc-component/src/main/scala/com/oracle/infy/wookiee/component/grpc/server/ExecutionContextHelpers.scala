package com.oracle.infy.wookiee.component.grpc.server

import cats.data.EitherT
import cats.effect.IO
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.grpc.errors.Errors.{UnknownWookieeGrpcError, WookieeGrpcError}
import com.oracle.infy.wookiee.grpc.utils.implicits._
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.Config
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.{Executors, ForkJoinPool, ScheduledThreadPoolExecutor, ThreadFactory}
import scala.concurrent.ExecutionContext

trait ExecutionContextHelpers extends LoggingAdapter {

  protected[oracle] def uncaughtExceptionHandler: UncaughtExceptionHandler = (t: Thread, e: Throwable) => {
    log.error(s"ECH400: Got an uncaught exception $e on thread ${t.getName}", e)
  }

  // Be sure threadNames is unique or it could lead to deadlocks
  // If parallelism = None then we'll use a cached thread pool with no size limit, if Some(1) we'll use a single thread pool
  def createEC(
      threadNames: String,
      parallelism: Option[Int] = None // scalafix:ok
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
      prefix: String
  )(implicit config: Config): EitherT[IO, WookieeGrpcError, ScheduledThreadPoolExecutor] = {
    IO {
      val tp = new ScheduledThreadPoolExecutor(
        config.getInt(s"${GrpcManager.ComponentName}.dispatcher-threads"),
        threadFactory(s"${config.getString(s"${GrpcManager.ComponentName}.thread-prefix")}-$prefix-scheduled")
      )
      tp.setRemoveOnCancelPolicy(true)
      tp
    }.toEitherT(err => UnknownWookieeGrpcError(err.getMessage))
  }

  def appLogger: EitherT[IO, WookieeGrpcError, Logger[IO]] = {
    Slf4jLogger
      .create[IO]
      .map(l => l: Logger[IO])
      .toEitherT(err => UnknownWookieeGrpcError(err.getMessage))
  }

  // club redundancy club...
  protected def forkJoinWorkerThreadFactoryFactory(prefix: String): ForkJoinWorkerThreadFactory = {
    pool: ForkJoinPool =>
      val worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
      worker.setName(s"$prefix-${worker.getPoolIndex}")
      worker
  }

  protected[oracle] def threadFactory(prefix: String): ThreadFactory = (r: Runnable) => {
    val t = new Thread(r)
    t.setName(s"$prefix-${t.getId.toString}")
    t.setUncaughtExceptionHandler(uncaughtExceptionHandler)
    t.setDaemon(true)
    t
  }
}
