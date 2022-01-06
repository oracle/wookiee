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

package com.oracle.infy.wookiee

import akka.actor.{Actor, UnhandledMessage}
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author Michael Cuthbert on 11/21/14.
 */
class UnhandledEventListener extends Actor {

  val externalLogger: Logger = LoggerFactory.getLogger(this.getClass)

  override def receive: Receive = {
    case message:UnhandledMessage =>
      //externalLogger.error(s"CRITICAL! No actors found for message ${message.getMessage} " +
        //s"for expected recipient ${message.getRecipient().path.toString}")

      externalLogger.debug(s"CRITICAL! No actors found for message ${message.getMessage} " +
        s"for expected recipient ${message.getRecipient().path.toString}")
  }
}
