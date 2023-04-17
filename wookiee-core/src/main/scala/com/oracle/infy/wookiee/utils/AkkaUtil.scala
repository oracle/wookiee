package com.oracle.infy.wookiee.utils

import akka.util.Timeout
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit

object AkkaUtil {

  val externalLogger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
    * Gets the default timeout from a config
    *
    * @param config  config containing the timeout
    * @param path    path to the timeout
    * @param unit    TimeUnit
    * @param default default if not found in config
    * @return Option value with Timeout
    */
  def getDefaultTimeout(config: Config, path: String, default: Timeout, unit: TimeUnit = TimeUnit.SECONDS): Timeout = {
    if (config.hasPath(path)) {
      val duration = config.getDuration(path, unit)
      Timeout(duration, unit)
    } else {
      default
    }
  }

}
