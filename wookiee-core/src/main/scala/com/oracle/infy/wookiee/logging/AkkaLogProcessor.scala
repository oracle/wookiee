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

import akka.event.DummyClassForStringSources
import org.slf4j.LoggerFactory

private[oracle] trait AkkaLogProcessor extends BaseLogProcessor {

  val empty = ""
  val emptyTemplate = "{}"

  /**
    * Process akka logging events
    * @param event the akka logging event
    */
  def process(event: akka.event.Logging.LogEvent): Unit = {
    val logger = getLogger(event)

    event match {
      case e: akka.event.Logging.Error =>
        e.cause match {
          case akka.event.Logging.Error.NoCause | null =>
            withContext(event.thread, event.timestamp, Some(event.logSource)) {
              logger.error(if (e.message != null) e.message.toString else empty, None)
            }

          case cause =>
            withContext(event.thread, event.timestamp, Some(event.logSource)) {
              logger.error(if (e.message != null) e.message.toString else cause.getLocalizedMessage, e.cause)
            }
        }

      case e: akka.event.Logging.Warning =>
        withContext(event.thread, event.timestamp, Some(event.logSource)) {
          logger.warn(emptyTemplate, transformParams(Seq(e.message.asInstanceOf[AnyRef])): _*)
        }

      case e: akka.event.Logging.Info =>
        withContext(event.thread, event.timestamp, Some(event.logSource)) {
          logger.info(emptyTemplate, transformParams(Seq(e.message.asInstanceOf[AnyRef])): _*)
        }

      case e: akka.event.Logging.Debug =>
        withContext(event.thread, event.timestamp, Some(event.logSource)) {
          logger.debug(emptyTemplate, transformParams(Seq(e.message.asInstanceOf[AnyRef])): _*)
        }
    }
  }

  private def getLogger(event: akka.event.Logging.LogEvent): org.slf4j.Logger = {
    event.logClass match {
      case c if c == classOf[DummyClassForStringSources] => LoggerFactory getLogger event.logSource
      case _                                             => LoggerFactory getLogger event.logClass
    }
  }
}
