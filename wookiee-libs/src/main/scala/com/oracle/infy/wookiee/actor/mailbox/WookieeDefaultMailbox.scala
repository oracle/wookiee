package com.oracle.infy.wookiee.actor.mailbox

import com.oracle.infy.wookiee.actor.WookieeActor

import java.util.concurrent.ConcurrentLinkedQueue

trait WookieeDefaultMailbox {
  private val queue = new ConcurrentLinkedQueue[(Any, WookieeActor)]()

  def enqueueMessage(message: Any)(implicit sender: WookieeActor): Unit = {
    queue.offer((message, sender))
    ()
  }

  def dequeueMessage(): (Any, WookieeActor) = queue.poll()
}
