/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.oracle.infy.wookiee.logging

import ch.qos.logback.classic.Level
import org.slf4j.{Logger => SlfLogger, LoggerFactory}

private[oracle] trait Slf4jLogging extends LogProcessor with AkkaLogProcessor {

  protected def getRootLevel: Level = {
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
      case classic: ch.qos.logback.classic.Logger => classic.getLevel
      case logger                                 => translateLevel(logger)
    }
  }

  def setLogLevel(level: Level): Unit = {
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
      case classic: ch.qos.logback.classic.Logger => classic.setLevel(level)
      case log =>
        log.info(
          s"Not using 'ch.qos.logback.classic.Logger', actually using '${log.getClass}', so not changing level to $level"
        )
    }
  }

  private def translateLevel(logger: SlfLogger): Level = {
    if (logger.isTraceEnabled) Level.TRACE
    else if (logger.isDebugEnabled) Level.DEBUG
    else if (logger.isInfoEnabled) Level.INFO
    else if (logger.isWarnEnabled) Level.WARN
    else Level.ERROR
  }
}
