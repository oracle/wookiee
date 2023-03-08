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

import akka.actor._
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.health.HealthComponent

import scala.concurrent.Future

object ${component-name}Actor {
  def props = Props(classOf[${component-name}Actor])
}

class ${component-name}Actor extends HActor {

  override def receive = super.receive

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = super.getHealth
}