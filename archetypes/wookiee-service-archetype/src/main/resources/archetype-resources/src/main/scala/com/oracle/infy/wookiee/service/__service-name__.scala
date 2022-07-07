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

import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.service.messages.Ready

class ${service-name} extends Service {

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands = {
    addCommand(${service-name}Command.CommandName, classOf[${service-name}Command])
  }

  /**
   * This is the receive expression for your service. Apply any logic you wish
   * to handle specific messages
   */
  override def serviceReceive = ({
    // TODO: Add additional message handlers here
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
  }: Receive) orElse super.serviceReceive
}

