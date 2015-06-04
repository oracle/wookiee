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
package com.webtrends.service.sockorest

import akka.util.Timeout
import com.webtrends.harness.command.{Command, CommandBean, CommandException, CommandResponse}
import com.webtrends.harness.component.ComponentHelper
import com.webtrends.harness.component.socko.route.SockoPut
import com.webtrends.service.Person

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class SUpdate extends Command
    with SockoPut
    with ComponentHelper {

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(2 seconds)

  override def path: String = "/person"

  override def unmarshall[T <: AnyRef : Manifest](obj: Array[Byte], contentType: String = "application/json"): Option[T] = {
    super.unmarshall[Person](obj).asInstanceOf[Some[T]]
  }

  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = SUpdate.CommandName

  /**
   * In this example the update and create commands are no different, however logic can be applied
   * to make this a correct update and also applied to the create command to make it a correct create
   *
   * @return
   */
  def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    val p = Promise[CommandResponse[T]]
    bean match {
      case Some(b) =>
        getComponent("wookiee-cache-memcache") onComplete {
          case Success(actor) =>
            // If we were doing a real API we might want to check the cache to see if it
            // exists first and if it does then throw some sort of exception, but this is just an example
            //val person = b(CommandBean.KeyEntity).asInstanceOf[JObject].extract[Person]
            // use line below instead of line above if overriding the setRoutes and extract the Person directly
            val person = b(CommandBean.KeyEntity).asInstanceOf[Person]
            person.writeInCache(actor) onComplete {
              case Success(s) =>
                p success CommandResponse[T](Some(person.asInstanceOf[T]), "json")
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("SUpdate", "Cache not initialized")
    }
    p.future
  }
}

object SUpdate {
  def CommandName = "SUpdate"
}