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
package com.webtrends.service.rest

import akka.util.Timeout
import com.webtrends.harness.command.{CommandException, Command, CommandResponse, CommandBean}
import com.webtrends.harness.component.ComponentHelper
import com.webtrends.harness.component.spray.route.SprayGet
import com.webtrends.service.Person
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ReadJSON extends Command
    with SprayGet
    with ComponentHelper {

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(2 seconds)

  override def path: String = "/person/json/$name"

  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = ReadJSON.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    val p = Promise[CommandResponse[T]]
    bean match {
      case Some(b) =>
        getComponent("wookiee-cache-memcache") onComplete {
          case Success(actor) =>
            val personName = b("name").asInstanceOf[String]
            Person(personName).readFromCache(actor) onComplete {
              case Success(person) =>
                person match {
                  case Some(per) => p success CommandResponse[T](Some(per.asInstanceOf[T]), "json")
                  case None => p failure CommandException("PersonRead", s"Person not found with name [$personName]")
                }
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("ReadJSON", "Cache not initialized")
    }
    p.future
  }
}

object ReadJSON {
  def CommandName = "ReadJSON"
}