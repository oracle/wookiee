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
package com.oracle.infy.wookiee.component.${component-name.toLowerCase()}

import com.oracle.infy.wookiee.component.Component

case class ${component-name}Message()

class ${component-name}Manager(name:String) extends Component(name) with ${component-name} {

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive = super.receive orElse {
    case ${component-name}Message => "DO SOMETHING HERE"
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    start${component-name}
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    stop${component-name}
    super.stop
  }
}