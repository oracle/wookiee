package com.oracle.infy.wookiee.app

import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ClassUtil

trait WookieeShutdown extends LoggingAdapter {

  /**
    * Any logic to run once we get the shutdown message but before we begin killing executors
    */
  def prepareForShutdown(): Unit =
    log.debug(s"COMP400: [${ClassUtil.getSimpleNameSafe(getClass)}] prepared for shutdown")
}
