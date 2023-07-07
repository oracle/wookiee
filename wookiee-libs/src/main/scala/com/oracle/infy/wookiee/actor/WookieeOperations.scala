package com.oracle.infy.wookiee.actor

trait WookieeOperations {
  val lock: AnyRef = new Object()

  def lockedOperation[T](operation: => T): T = {
    lock.synchronized {
      operation
    }
  }
}
