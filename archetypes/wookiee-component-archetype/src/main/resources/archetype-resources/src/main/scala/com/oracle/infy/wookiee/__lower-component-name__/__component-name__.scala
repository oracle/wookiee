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

import akka.actor.ActorRef
import com.oracle.infy.wookiee.component.Component

trait ${component-name} { this: Component =>

  var ${component-name}Ref:Option[ActorRef] = None

  def start${component-name} : ActorRef = {
    ${component-name}Ref = Some(context.actorOf(${component-name}Actor.props, ${component-name}.${component-name}Name))
    ${component-name}Ref.get
  }

  def stop${component-name} = {
    //TODO execute any special logic here to shut down the component
  }
}

object ${component-name} {
  val ${component-name}Name = "${component-name}"
}