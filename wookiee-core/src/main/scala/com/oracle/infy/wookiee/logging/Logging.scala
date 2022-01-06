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

import java.util.logging.Level
import java.util.logging.Level._

import akka.actor.Actor

import scala.util.Try

/**
 * Use this trait to include in your actor so that there is logging support.
 * This is a substitute for akka's ActorLogging trait
 */
trait ActorLoggingAdapter extends LoggingAdapter {
  this: Actor =>
  @transient
  override protected lazy val log: Logger = context match {
    case null =>
      // log may not be called until an asynchronous callback in which case
      // context will be null if the actor has already been stopped
      Logger(getClass)
    case _ =>
      Logger(this, context.system)
  }
}

/**
 * Use this trait in your class so that there is logging support
 */
trait LoggingAdapter {
  @transient
  protected lazy val log: Logger = Logger(getClass)

  // Will log the error if the input function throws one and return a Try
  def tryAndLogError[A](f: => A, messageOnFail: Option[String] = None, level: Level = WARNING): Try[A] = {
    val tried = Try(f)
    if (tried.isFailure) {
      val ex = tried.failed.get
      val message = messageOnFail.getOrElse(ex.getMessage)

      level match {
        case SEVERE => log.error(message, ex)
        case INFO => log.info(message, ex)
        case FINE => log.info(message, ex)
        case CONFIG => log.debug(message, ex)
        case FINER => log.debug(message, ex)
        case FINEST => log.trace(message, ex)
        case WARNING => log.warn(message, ex)
        case _ => log.warn(message, ex)
      }
    }
    tried
  }
}