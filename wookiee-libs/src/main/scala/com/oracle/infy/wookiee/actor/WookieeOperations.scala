package com.oracle.infy.wookiee.actor

trait WookieeOperations {
  protected[wookiee] val lock: AnyRef = new Object()

  protected def lockedOperation[T](operation: => T): T = {
    lock.synchronized {
      operation
    }
  }
}
