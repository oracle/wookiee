package com.oracle.infy.wookiee.qa.duplicate

import com.oracle.infy.wookiee.logging.LoggingAdapter

object DuplicateClass extends LoggingAdapter {
  private val inst = "A"

  def logInfo(compName: String): String = {
    log.info(s"Class hash code: '${this.hashCode()}'")
    log.info(s"Calling Instance: '$compName'")
    log.info(s"This Instance: '$inst'")
    inst
  }
}
