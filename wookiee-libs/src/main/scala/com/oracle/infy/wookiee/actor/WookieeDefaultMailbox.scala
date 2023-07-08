package com.oracle.infy.wookiee.actor

import java.util.concurrent.ConcurrentLinkedQueue

trait WookieeDefaultMailbox {
  private val queue = new ConcurrentLinkedQueue[(Any, WookieeActor)]()

  def enqueueMessage(message: Any)(implicit sender: WookieeActor): Unit = {
    queue.offer((message, sender))
    ()
  }

  def dequeueMessage(): (Any, WookieeActor) = queue.poll()
}
