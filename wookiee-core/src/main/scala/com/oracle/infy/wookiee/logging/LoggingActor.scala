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

import akka.actor.Actor
import akka.event.Logging.{InitializeLogger, LoggerInitialized}
import com.oracle.infy.wookiee.health.ActorHealth

class LoggingActor extends Actor with ActorHealth with Slf4jLogging with ActorLoggingAdapter {

  // Are we routing non-akka logging events to this actor for processing
  val routeLogging: Boolean = context.system.settings.config.getBoolean("logging.use-actor")

  override def preStart(): Unit = {
    if (routeLogging) {
      Logger.registerMediator(self)
    }
    log.info("Logging Manager started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    if (routeLogging) {
     Logger.unregisterMediator(self)
    }
    log.info("Logging Manager started: {}", context.self.path)
  }

  def receive: Receive = health orElse {
    case InitializeLogger(_) =>
      sender() ! LoggerInitialized

    // webtrends log events
    case event: LogEvent => process(event)

    // akka events
    case event: akka.event.Logging.LogEvent => process(event)
  }

}