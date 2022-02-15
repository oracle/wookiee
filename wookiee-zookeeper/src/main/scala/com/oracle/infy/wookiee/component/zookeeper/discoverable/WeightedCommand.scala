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

package com.oracle.infy.wookiee.component.zookeeper.discoverable

import com.oracle.infy.wookiee.command.Command

import scala.concurrent.Future
import scala.reflect.ClassTag

case class Weight(weight: Int, forceSet: Boolean)

abstract class WeightedCommand[Input <: Product: ClassTag, Output <: Any: ClassTag] extends Command[Input, Output] {
  this: Discoverable =>

  val basePath: String
  val commandId: String

  def updateWeight(weight: Int, forceSet: Boolean): Future[Boolean] =
    updateWeight(weight, basePath, commandId, forceSet)

  override def receive: Receive =
    ({
      case Weight(w, f) => updateWeight(w, f); ()
    }: Receive) orElse super.receive
}
