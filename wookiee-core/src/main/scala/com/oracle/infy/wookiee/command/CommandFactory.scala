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

import akka.actor.Props

import scala.concurrent.Future
import scala.reflect.ClassTag

object CommandFactory {

  def createCommand[U <: Any: ClassTag, V <: Any: ClassTag](
      businessLogic: U => Future[V]
  ): Props = {
    class FunctionalCommand extends Command[U, V] {
      override def execute(bean: U): Future[V] =
        businessLogic(bean)
    }
    object FunctionalCommand {
      def apply() = new FunctionalCommand()
    }
    Props({ FunctionalCommand() })
  }

  def createCommand[U <: Any: ClassTag, V <: Any: ClassTag](
      customUnmarshaller: Bean => U,
      businessLogic: U => Future[V],
      customMarshaller: V => Array[Byte]
  ): Props = {

    class FunctionalCommand extends Command[Bean, Array[Byte]] {
      import context.dispatcher

      override def execute(bean: Bean): Future[Array[Byte]] = {
        val input = customUnmarshaller(bean)
        businessLogic(input) map customMarshaller
      }
    }
    object FunctionalCommand {
      def apply() = new FunctionalCommand()
    }
    Props({ FunctionalCommand() })
  }
}
