package com.oracle.infy.wookiee.utils

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.oracle.infy.wookiee.actor.{WookieeActor, WookieeScheduler}
import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent._
import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ThreadUtil extends LoggingAdapter with WookieeScheduler {

  // Be sure threadNames is unique or it could lead to deadlocks
  // If parallelism = None then we'll use a cached thread pool with no size limit, if Some(1) we'll use a single thread pool
  def createEC(threadNames: String): ExecutionContext = createEC(threadNames, None)

  /**
    * Creates an ExecutionContext with the given thread name prefix and parallelism
    * @param threadNames - Be sure threadNames is unique or it could lead to deadlocks
    * @param parallelism - If parallelism = None then we'll use a cached thread pool with no size limit, if Some(1) we'll use a single thread pool
    * @return - ExecutionContext
    */
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

  // Creates an IORuntime with the given thread name prefix and parallelism
  def ioRuntime(
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      scheduled: ScheduledThreadPoolExecutor
  ): IORuntime =
    IORuntime(mainEC, blockingEC, Scheduler.fromScheduledExecutor(scheduled), () => (), IORuntimeConfig())

  // Creates a ScheduledThreadPoolExecutor with the given thread name prefix and parallelism
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
      waitMs: Long = 15000L,
      ignoreError: Boolean = true
  ): T = {
    val goUntil = System.currentTimeMillis + waitMs
    while (System.currentTimeMillis < goUntil) {
      try {
        f match {
          case Some(result) =>
            return result
          case None =>
            Thread.sleep(500L)
        }
      } catch {
        case e: Exception =>
          if (ignoreError && System.currentTimeMillis < goUntil) {
            Thread.sleep(500L)
          } else {
            throw e
          }
      }
    }

    throw new RuntimeException("Timed out waiting for result")
  }

  // A helper method to wait for a true evaluation, great for testing
  def awaitEvent(
      f: => Boolean,
      waitMs: Long = 15000L,
      ignoreError: Boolean = true
  ): Unit = {
    val goUntil = System.currentTimeMillis + waitMs
    while (System.currentTimeMillis < goUntil) {
      try {
        if (f)
          return
        else
          Thread.sleep(500L)
      } catch {
        case e: Exception =>
          if (ignoreError && System.currentTimeMillis < goUntil) {
            Thread.sleep(500L)
          } else {
            throw e
          }
      }
    }

    throw new RuntimeException("Timed out waiting for result")
  }

  // A helper method to wait for a future to be true, great for testing
  // Note that this method does block, try `futureWithTimeout` if looking for an unblocking pod-ready solution
  // `f` has to be a function that returns a future, so that it re-evaluates every time
  // Example:
  // def futureToTest(): Future[Boolean] = Future { true }
  // awaitFuture(futureToTest)
  // @throws RuntimeException if the future never returns true
  def awaitFuture(
      f: () => Future[Boolean],
      waitMs: Long = 15000L,
      retryIntervalMs: Long = 500L,
      ignoreError: Boolean = true
  ): Unit = {
    val goUntil = System.currentTimeMillis + waitMs

    @tailrec
    def retry(): Unit = {
      if (System.currentTimeMillis < goUntil) {
        val futureResult = Try(Await.result(f(), retryIntervalMs.milliseconds))

        futureResult match {
          case Success(true)             => return
          case Success(false)            => Thread.sleep(retryIntervalMs)
          case Failure(_) if ignoreError => Thread.sleep(retryIntervalMs)
          case Failure(exception)        => throw exception
        }

        retry()
      } else {
        throw new RuntimeException("Timed out waiting for result")
      }
    }

    retry()
  }

  // Highly performant and non-blocking helper function to return from a Future early if 'timeout' is reached before completion
  // Note that Scala doesn't support Future cancellation so the original Future will continue its execution until completion (or hang forever, if it has a bug)
  // @throws (as a Future Failure) java.util.concurrent.TimeoutException if original Future doesn't return in time
  def futureWithTimeout[T](future: Future[T], timeout: java.time.Duration)(implicit ec: ExecutionContext): Future[T] =
    futureWithTimeout(future, Duration.fromNanos(timeout.toNanos))

  def futureWithTimeout[T](future: Future[T], timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[T] = {
    val promise = Promise[T]()

    // Schedule a timeout
    val runnable = new Runnable {
      def run(): Unit = {
        promise.tryFailure(new TimeoutException("Future timed out!"))
        ()
      }

    }
    scheduleOnce(timeout, runnable)

    // Complete the promise with the future's result if it completes first
    future.onComplete { result =>
      promise.tryComplete(result)
    }

    // Return a future that either completes with the original future's result or the timeout
    promise.future
  }

  // A helper method to ask an actor and wait for a response, great for testing
  def awaitResponse[T: ClassTag](
      actor: WookieeActor,
      message: Any,
      waitMs: Long = 5000L
  ): T =
    Await.result((actor ? message).mapTo[T], waitMs.millis)
}
