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
import com.webtrends.harness.component.spray.route.SprayPost
import com.webtrends.service.{PersonService, Person}
import spray.http.{HttpCharsets, HttpEntity, StatusCodes}
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

class Create extends Command
    with SprayPost
    with ComponentHelper {

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(2 seconds)

  // Below is a custom unmarshaller if you want to use it differently
  // The Create command will use a custom unmarshaller and mimetype
  // and the Update command will use the default unmarshaller using JSON
  override implicit def InputUnmarshaller[T:Manifest] =
    Unmarshaller[T](PersonService.`application/vnd.webtrends.person`) {
      case x: HttpEntity.NonEmpty ⇒
        val Array(name, age) = x.asString(defaultCharset = HttpCharsets.`UTF-8`).split(":")
        Person(name, Integer.parseInt(age)).asInstanceOf[T]
    }

  // Below is a custom marshaller if you want to use it differently
  // The Create command will use a custom marshaller and mimetype
  // and the Update command will use the default marshaller
  override implicit def OutputMarshaller[T <:AnyRef] =
    Marshaller.of[T](PersonService.`application/vnd.webtrends.person`) { (value, contentType, ctx) =>
      val Person(name, age) = value
      val string = "Person: %s, %s".format(name, age)
      ctx.marshalTo(HttpEntity(contentType, string))
    }

  override def responseStatusCode: StatusCodes.Success = StatusCodes.Created

  override def path: String = "/person"

  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = Create.CommandName

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
            // If we were doing a real API we might want to check the cache to see if it
            // exists first and if it does then throw some sort of exception, but this is just an example
            val person = b(CommandBean.KeyEntity).asInstanceOf[Person]
            person.writeInCache(actor) onComplete {
              case Success(s) => p success CommandResponse[T](Some(person.asInstanceOf[T]), PersonService.PersonMimeType)
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("Create", "Bean not set")
    }
    p.future
  }
}

object Create {
  def CommandName = "Create"
}