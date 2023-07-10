package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.utils.{ClassUtil, ThreadUtil}

import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration

trait WookieeScheduler {

  private implicit val schedulerEc: ExecutionContext =
    ThreadUtil.createEC(s"wookiee-scheduler-${ClassUtil.getSimpleNameSafe(getClass)}")
  private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  // This function provides a non-blocking "sleep" by scheduling the completion of a Promise
  private def sleep(duration: FiniteDuration): Future[Unit] = {
    val p = Promise[Unit]()
    timer.schedule(new Runnable {
      def run(): Unit = {
        p.success(())
      }
    }, duration.length, duration.unit)
    p.future
  }

  // This method schedules a one-time event that sends a message to the receiver after a specified delay
  def scheduleOnce(delay: FiniteDuration, receiver: WookieeActor, message: Any): Unit = {
    sleep(delay).map(_ => receiver ! message)
    ()
  }

  // This method schedules a recurring event that sends a message to the receiver after initial delay and at subsequent intervals
  def schedule(delay: FiniteDuration, interval: FiniteDuration, receiver: WookieeActor, message: Any): Unit = {
    def recursiveSchedule(): Unit = {
      sleep(interval).map { _ =>
        receiver ! message
        recursiveSchedule()
      }
      ()
    }
    // First, handle the initial delay
    sleep(delay).map(_ => {
      receiver ! message
      // Then, start the recurring task
      recursiveSchedule()
    })
    ()
  }

  def scheduleOnce(delay: FiniteDuration)(f: => Unit): Unit =
    scheduleOnce(delay, new Runnable {
      override def run(): Unit = f
    })

  def scheduleOnce(delay: FiniteDuration, runnable: Runnable): Unit = {
    // Create a non-blocking delay, after which execute the given Runnable task
    sleep(delay).map(_ => runnable.run())
    ()
  }
}
