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

package com.oracle.infy.wookiee.command

import akka.pattern.pipe
import com.oracle.infy.wookiee.app.HActor

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * A command should be a business logic that handles messages from some source (like http) then executes
  * the logic that is defined for the end point and returns the response. It should not ever deal with the
  * underlying messaging protocol but rather focus exclusively on the business logic. It should also not
  * deal with any storage or caching or anything like that.
  */
abstract class Command[Input <: Any: TypeTag: ClassTag, Output <: Any: TypeTag] extends HActor with CommandHelper {
  import context.dispatcher

  override def receive: Receive =
    health orElse ({
      case ExecuteCommand(_, bean: Input, _) =>
        pipe(execute(bean)) to sender()
        ()
      case _ => // ignore all other messages to this actor
    }: Receive)

  def execute(bean: Input): Future[Output]
}

object CommandBeanHelper {
  def createInput[Input <: Any: TypeTag: ClassTag](bean: Bean): Input = Bean.infer[Input](bean)
}
