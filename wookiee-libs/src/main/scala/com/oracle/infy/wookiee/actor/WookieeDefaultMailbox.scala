package com.oracle.infy.wookiee.actor

import java.util.concurrent.LinkedBlockingQueue

trait WookieeDefaultMailbox {
  private val queue = new LinkedBlockingQueue[(Any, WookieeActor)]()

  def enqueueMessage(message: Any)(implicit sender: WookieeActor): Unit = {
    queue.offer((message, sender))
    ()
  }

  def dequeueMessage(): (Any, WookieeActor) = queue.take()
}
