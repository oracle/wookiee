/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.logging

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LogSource
import org.slf4j.{LoggerFactory, Marker, Logger => Underlying}

import scala.language.experimental.macros

object Logger {

  private[logging] var mediator: Option[ActorRef] = None

  private[harness] def registerMediator(actor: ActorRef) = {
    mediator = Some(actor)
  }

  private[harness] def unregisterMediator(actor: ActorRef) = {
    mediator = None
  }

  def apply(logger: String): Logger = {
    require(logger != null, "Logger name must not be null!")
    new Logger(LoggerFactory getLogger logger, None)
  }

  def apply(logClass: Class[_]): Logger = {
    require(logClass != null, "Class name must not be null!")
    new Logger(LoggerFactory getLogger logClass, None)
  }

  def apply[T: LogSource](o: T, system: ActorSystem): Logger = {
    require(o != null, "Log source must not be null!")
    require(system != null, "ActorSystem must not be null!")
    val src = LogSource(o, system)
    new Logger(LoggerFactory getLogger src._2, Some(src._1))
  }

  def getLogger(logger: String): Logger = apply(logger)

  def getLogger(logClass: Class[_]): Logger = apply(logClass)

}

final class Logger private(val logger: Underlying, val altSource: Option[String] = None) extends Slf4jLogging {

  private def publish(event: LogEvent) = Logger.mediator match {
    case Some(actor) => actor ! event
    case None => process(event)
  }

  def error(message: String): Unit =
    if (logger.isErrorEnabled) publish(Error(logger, message, None, altSource))

  def error(message: String, t: Throwable): Unit =
    if (logger.isErrorEnabled) publish(Error(logger, message, None, altSource, Nil, Some(t)))

  def error(message: String, params: Any*): Unit =
    if (logger.isErrorEnabled) publish(Error(logger, message, None, altSource, params))

  def error(t: Throwable, message: String, params: Any*): Unit =
    if (logger.isErrorEnabled) publish(Error(logger, message, None, altSource, params, Some(t)))

  def error(marker: Marker, message: String): Unit =
    if (logger.isErrorEnabled(marker)) publish(Error(logger, message, Some(marker), altSource))

  def error(marker: Marker, message: String, t: Throwable): Unit =
    if (logger.isErrorEnabled) publish(Error(logger, message, Some(marker), altSource, Nil, Some(t)))

  def error(marker: Marker, message: String, params: Any*): Unit =
    if (logger.isErrorEnabled(marker)) publish(Error(logger, message, Some(marker), altSource, params))

  def error(marker: Marker, t: Throwable, message: String, params: Any*): Unit =
    if (logger.isErrorEnabled(marker)) publish(Error(logger, message, Some(marker), altSource, Nil, Some(t)))

  // Warn
  def warn(message: String): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource))

  def warning(message: String): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource))

  def warn(message: String, t: Throwable): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource, Nil, Some(t)))

  def warn(message: String, params: Any*): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource, params))

  def warning(message: String, params: Any*): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource, params))

  def warn(t: Throwable, message: String, params: Any*): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, None, altSource, params, Some(t)))

  def warn(marker: Marker, message: String): Unit =
    if (logger.isWarnEnabled(marker)) publish(Warn(logger, message, Some(marker), altSource))

  def warn(marker: Marker, message: String, t: Throwable): Unit =
    if (logger.isWarnEnabled) publish(Warn(logger, message, Some(marker), altSource, Nil, Some(t)))

  def warn(marker: Marker, message: String, params: Any*): Unit =
    if (logger.isWarnEnabled(marker)) publish(Warn(logger, message, Some(marker), altSource, params))

  def warn(marker: Marker, t: Throwable, message: String, params: Any*): Unit =
    if (logger.isWarnEnabled(marker)) publish(Warn(logger, message, Some(marker), altSource, Nil, Some(t)))


  // Info
  def info(message: String): Unit =
    if (logger.isInfoEnabled) publish(Info(logger, message, None, altSource))

  def info(message: String, t: Throwable): Unit =
    if (logger.isInfoEnabled) publish(Info(logger, message, None, altSource, Nil, Some(t)))

  def info(message: String, params: Any*): Unit =
    if (logger.isInfoEnabled) publish(Info(logger, message, None, altSource, params))

  def info(t: Throwable, message: String, params: Any*): Unit =
    if (logger.isInfoEnabled) publish(Info(logger, message, None, altSource, params, Some(t)))

  def info(marker: Marker, message: String): Unit =
    if (logger.isInfoEnabled(marker)) publish(Info(logger, message, Some(marker), altSource))

  def info(marker: Marker, message: String, t: Throwable): Unit =
    if (logger.isInfoEnabled) publish(Info(logger, message, Some(marker), altSource, Nil, Some(t)))

  def info(marker: Marker, message: String, params: Any*): Unit =
    if (logger.isInfoEnabled(marker)) publish(Info(logger, message, Some(marker), altSource, params))

  def info(marker: Marker, t: Throwable, message: String, params: Any*): Unit =
    if (logger.isInfoEnabled(marker)) publish(Info(logger, message, Some(marker), altSource, Nil, Some(t)))


  // Debug
  def debug(message: String): Unit =
    if (logger.isDebugEnabled) publish(Debug(logger, message, None, altSource))

  def debug(message: String, t: Throwable): Unit =
    if (logger.isDebugEnabled) publish(Debug(logger, message, None, altSource, Nil, Some(t)))

  def debug(message: String, params: Any*): Unit =
    if (logger.isDebugEnabled) publish(Debug(logger, message, None, altSource, params))

  def debug(t: Throwable, message: String, params: Any*): Unit =
    if (logger.isDebugEnabled) publish(Debug(logger, message, None, altSource, params, Some(t)))

  def debug(marker: Marker, message: String): Unit =
    if (logger.isDebugEnabled(marker)) publish(Debug(logger, message, Some(marker)))

  def debug(marker: Marker, message: String, t: Throwable): Unit =
    if (logger.isDebugEnabled) publish(Debug(logger, message, Some(marker), altSource, Nil, Some(t)))

  def debug(marker: Marker, message: String, params: Any*): Unit =
    if (logger.isDebugEnabled(marker)) publish(Debug(logger, message, Some(marker), altSource, params))

  def debug(marker: Marker, t: Throwable, message: String, params: Any*): Unit =
    if (logger.isDebugEnabled(marker)) publish(Debug(logger, message, Some(marker), altSource, Nil, Some(t)))


  // Trace
  def trace(message: String): Unit =
    if (logger.isTraceEnabled) publish(Trace(logger, message, None, altSource))

  def trace(message: String, t: Throwable): Unit =
    if (logger.isTraceEnabled) publish(Trace(logger, message, None, altSource, Nil, Some(t)))

  def trace(message: String, params: Any*): Unit =
    if (logger.isTraceEnabled) publish(Trace(logger, message, None, altSource, params))

  def trace(t: Throwable, message: String, params: Any*): Unit =
    if (logger.isTraceEnabled) publish(Trace(logger, message, None, altSource, params, Some(t)))

  def trace(marker: Marker, message: String): Unit =
    if (logger.isTraceEnabled(marker)) publish(Trace(logger, message, Some(marker), altSource))

  def trace(marker: Marker, message: String, t: Throwable): Unit =
    if (logger.isTraceEnabled) publish(Trace(logger, message, Some(marker), altSource, Nil, Some(t)))

  def trace(marker: Marker, message: String, params: Any*): Unit =
    if (logger.isTraceEnabled(marker)) publish(Trace(logger, message, Some(marker), altSource, params))

  def trace(marker: Marker, t: Throwable, message: String, params: Any*): Unit =
    if (logger.isTraceEnabled(marker)) publish(Trace(logger, message, Some(marker), altSource, Nil, Some(t)))

}
