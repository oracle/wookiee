package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeScheduler.{schedulerEc, timer}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ThreadUtil

import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

object WookieeScheduler extends LoggingAdapter {

  // Main processor for scheduled events
  private lazy val schedulerEc: ExecutionContext =
    ThreadUtil.createEC("wookiee-scheduler")
  private var timer: ScheduledExecutorService = Executors.newScheduledThreadPool(32)

  protected[wookiee] def setThreads(threads: Int): Unit = schedulerEc.synchronized {
    timer = Executors.newScheduledThreadPool(threads)
    log.info(s"Wookiee Scheduler Ready for Tasks: Pool Size = [$threads]")
  }
}

trait WookieeScheduler {

  // This function provides a non-blocking "sleep" by scheduling the completion of a Promise
  // Note that it uses a single-threaded executor, so functions scheduled with this method
  // should not block (unless you want them to)
  def sleep(duration: FiniteDuration): Future[Unit] = {
    val p = Promise[Unit]()
    timer.schedule(new Runnable {
      def run(): Unit = {
        p.success(())
      }
    }, duration.length, duration.unit)
    p.future
  }

  // This method schedules a one-time event that sends a message to the receiver after a specified delay
  def scheduleOnce(delay: FiniteDuration, receiver: WookieeActor, message: Any)(
      implicit execution: ExecutionContext = schedulerEc
  ): Unit = {
    sleep(delay).map(_ => receiver ! message)
    ()
  }

  // This method schedules a recurring event that sends a message to the receiver after initial delay and at subsequent intervals
  def schedule(delay: FiniteDuration, interval: FiniteDuration, receiver: WookieeActor, message: Any)(
      implicit execution: ExecutionContext = schedulerEc
  ): Unit = {
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
    scheduleOnce(delay, () => f)

  def scheduleOnce(delay: FiniteDuration, runnable: Runnable): Unit = {
    // Create a non-blocking delay, after which execute the given Runnable task
    sleep(delay).map(_ => runnable.run())(schedulerEc)
    ()
  }
}
