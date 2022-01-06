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

private[oracle] trait LogProcessor extends BaseLogProcessor {

  def process(event: LogEvent): Unit = event match {
    case e@(_: LogEvent) if e.marker.isDefined => processMarkerEvent(e)
    case e => processLoggerEvent(e)
  }

  private def processLoggerEvent(event: LogEvent): Unit = event match {
    case Error(logger, message, _, _, params, cause) =>
      cause match {
        case Some(t) => withContext(event.thread, event.timestamp, event.altSource) {
          logger.error(format(message, params), t)
        }
        case None => withContext(event.thread, event.timestamp, event.altSource) {
          logger.error(message, transformParams(params): _*)
        }
      }
    case Warn(logger, message, _, _, params, cause) =>
      cause match {
        case Some(t) => withContext(event.thread, event.timestamp, event.altSource) {
          logger.warn(format(message, params), t)
        }
        case None => withContext(event.thread, event.timestamp, event.altSource) {
          logger.warn(message, transformParams(params): _*)
        }
      }
    case Info(logger, message, _, _, params, cause) =>
      cause match {
        case Some(t) => withContext(event.thread, event.timestamp, event.altSource) {
          logger.info(format(message, params), t)
        }
        case None => withContext(event.thread, event.timestamp, event.altSource) {
          logger.info(message, transformParams(params): _*)
        }
      }
    case Debug(logger, message, _, _, params, cause) =>
      cause match {
        case Some(t) => withContext(event.thread, event.timestamp, event.altSource) {
          logger.debug(format(message, params), t)
        }
        case None => withContext(event.thread, event.timestamp, event.altSource) {
          logger.debug(message, transformParams(params): _*)
        }
      }
    case Trace(logger, message, _, _, params, cause) =>
      cause match {
        case Some(t) => withContext(event.thread, event.timestamp, event.altSource) {
          logger.trace(format(message, params), t)
        }
        case None => withContext(event.thread, event.timestamp, event.altSource) {
          logger.trace(message, transformParams(params): _*)
        }
      }
    case _ => // Do nothing

  }

  private def processMarkerEvent(event: LogEvent): Unit = event match {
    case Error(logger, message, Some(marker), _, params, cause) =>
      cause match {
        case Some(t) => logger.error(marker, format(message, params), t)
        case None => logger.error(marker, message, transformParams(params): _*)
      }
    case Warn(logger, message, Some(marker), _, params, cause) =>
      cause match {
        case Some(t) => logger.warn(marker, format(message, params), t)
        case None => logger.warn(marker, message, transformParams(params): _*)
      }
    case Info(logger, message, Some(marker), _, params, cause) =>
      cause match {
        case Some(t) => logger.info(marker, format(message, params), t)
        case None => logger.info(marker, message, transformParams(params): _*)
      }
    case Debug(logger, message, Some(marker), _, params, cause) =>
      cause match {
        case Some(t) => logger.debug(marker, format(message, params), t)
        case None => logger.debug(marker, message, transformParams(params): _*)
      }
    case Trace(logger, message, Some(marker), _, params, cause) =>
      cause match {
        case Some(t) => logger.trace(marker, format(message, params), t)
        case None => logger.trace(marker, message, transformParams(params): _*)
      }
    case _ => // Do nothing

  }
}