/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.command.{Command, CommandResponse, CommandBean}
import scala.concurrent.Future

class ${service-name}Command extends Command {
  import context.dispatcher

  /**
  * Name of the command that will be used for the actor name
  *
  * @return
  */
  override def commandName: String = ${service-name}Command.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future {
      CommandResponse(Some("TODO: All execute work here".asInstanceOf[T]))
    }
  }
}

object ${service-name}Command {
  def CommandName = "${service-name}"
}