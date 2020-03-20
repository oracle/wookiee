/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
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

package com.webtrends.harness.command

import akka.pattern.pipe
import com.webtrends.harness.app.HActor

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * A command should be a business logic that handles messages from some source (like http) then executes
 * the logic that is defined for the end point and returns the response. It should not ever deal with the
 * underlying messaging protocol but rather focus exclusively on the business logic. It should also not
 * deal with any storage or caching or anything like that.
 *
 * @author Michael Cuthbert on 12/1/14.
 */
abstract class Command[Input <: Product : ClassTag, Output <: Any : ClassTag] extends BaseCommand with HActor with CommandHelper {
  import context.dispatcher

  override def receive: Receive = health orElse ({
    case ExecuteCommand(_, bean:Input, _) =>
      pipe(execute(bean)) to sender
    case _ => // ignore all other messages to this actor
  } : Receive)

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   */
  def execute(bean: Input) : Future[Output]
}

object CommandBeanHelper {
  def createInput[Input:ClassTag](bean: Bean): Input = Bean.infer[Input](bean)
}