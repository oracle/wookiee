package com.oracle.infy.wookiee.actor

import scala.concurrent.duration.FiniteDuration

trait WookieeScheduler {

  def scheduleOnce(delay: FiniteDuration, receiver: WookieeActor, message: Any): Unit = {
    // Create a new thread that will sleep for the delay and then send the message
    val thread = new Thread(() => {
      Thread.sleep(delay.toMillis)
      receiver ! message
    })
    thread.start()
  }

  def schedule(delay: FiniteDuration, interval: FiniteDuration, receiver: WookieeActor, message: Any): Unit = {
    // Create a new thread that will sleep for the delay and then send the message
    val thread = new Thread(() => {
      Thread.sleep(delay.toMillis)
      while (true) {
        receiver ! message
        Thread.sleep(interval.toMillis)
      }
    })
    thread.start()
  }
}
