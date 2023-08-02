package com.oracle.infy.wookiee.logging

import cats.effect.IO
import org.slf4j.{Logger, LoggerFactory}

/**
  * Use this trait in your class so that there is logging support,
  * this adapter will return IO wrapped responses for each type of logging
  *
  * A good alternative to the log4cats logger that can use a common version of slf4j-api
  */
trait LoggingAdapterIO {

  @transient
  lazy val logIO: LoggerIO = LoggerIO(LoggerFactory.getLogger(getClass))
}

case class LoggerIO(underlying: Logger) {
  def trace(msg: String): IO[Unit] = IO(underlying.trace(msg))
  def debug(msg: String): IO[Unit] = IO(underlying.debug(msg))
  def info(msg: String): IO[Unit] = IO(underlying.info(msg))
  def warn(msg: String): IO[Unit] = IO(underlying.warn(msg))
  def error(msg: String): IO[Unit] = IO(underlying.error(msg))

  def trace(msg: String, t: Throwable): IO[Unit] = IO(underlying.trace(msg, t))
  def debug(msg: String, t: Throwable): IO[Unit] = IO(underlying.debug(msg, t))
  def info(msg: String, t: Throwable): IO[Unit] = IO(underlying.info(msg, t))
  def warn(msg: String, t: Throwable): IO[Unit] = IO(underlying.warn(msg, t))
  def error(msg: String, t: Throwable): IO[Unit] = IO(underlying.error(msg, t))

  def trace(msg: String, params: AnyRef*): IO[Unit] = IO(underlying.trace(msg, params: _*))
  def debug(msg: String, params: AnyRef*): IO[Unit] = IO(underlying.debug(msg, params: _*))
  def info(msg: String, params: AnyRef*): IO[Unit] = IO(underlying.info(msg, params: _*))
  def warn(msg: String, params: AnyRef*): IO[Unit] = IO(underlying.warn(msg, params: _*))
  def error(msg: String, params: AnyRef*): IO[Unit] = IO(underlying.error(msg, params: _*))

  def isTraceEnabled: IO[Boolean] = IO(underlying.isTraceEnabled)
  def isDebugEnabled: IO[Boolean] = IO(underlying.isDebugEnabled)
  def isInfoEnabled: IO[Boolean] = IO(underlying.isInfoEnabled)
  def isWarnEnabled: IO[Boolean] = IO(underlying.isWarnEnabled)
  def isErrorEnabled: IO[Boolean] = IO(underlying.isErrorEnabled)

  def log(logLogic: Logger => Unit): IO[Unit] = IO(logLogic(underlying))
}
