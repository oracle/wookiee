package com.oracle.infy.wookiee.logging

import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try
import java.util.logging.Level
import java.util.logging.Level._

/**
  * Use this trait in your class so that there is logging support
  */
trait LoggingAdapter {

  @transient
  lazy val log: Logger = LoggerFactory.getLogger(getClass)

  // Will log the error if the input function throws one and return a Try
  def tryAndLogError[A](f: => A, messageOnFail: Option[String] = None, level: Level = WARNING): Try[A] = {
    val tried = Try(f)
    if (tried.isFailure) {
      val ex = tried.failed.get
      val message = messageOnFail.getOrElse(ex.getMessage)

      level match {
        case SEVERE  => log.error(message, ex)
        case INFO    => log.info(message, ex)
        case FINE    => log.info(message, ex)
        case CONFIG  => log.debug(message, ex)
        case FINER   => log.debug(message, ex)
        case FINEST  => log.trace(message, ex)
        case WARNING => log.warn(message, ex)
        case _       => log.warn(message, ex)
      }
    }
    tried
  }
}
