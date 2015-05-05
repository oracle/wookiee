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

package com.webtrends.service

import com.webtrends.harness.command.{CommandResponse, CommandBean, Command}
import com.webtrends.harness.component.socko.route._
import com.webtrends.harness.component.spray.route._
import org.joda.time.{DateTimeZone, DateTime}
import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.routes.{Path, GET}
import spray.routing.Route

import scala.concurrent.Future

/**
 * @author Michael Cuthbert on 12/3/14.
 */
class PingCommand extends Command
// SPRAY Traits ---------
    with SprayGet
    with SprayCustom
    with SprayPost
    with SprayDelete
    with SprayOptions
    with SprayPatch
    //with SprayPut
    with SprayHead {
  implicit val executionContext = context.dispatcher
  override def path: String = "/test/$key/ping"

  override def customRoute: Route = {
    exceptionHandler {
      get {
        path("custom" / "ping") {
          innerExecute()
        }
      }
    }
  }

  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = PingCommand.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future {
      val dtime = new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString()
      bean match {
        case Some(b) =>
          b.appendMap(Map("pong" -> dtime))
          CommandResponse(Some(b.toMap.asInstanceOf[T]), "json")
        case None =>
          CommandResponse(Some("pong from httpexample: ".concat(dtime).asInstanceOf[T]), "txt")
      }
    }
  }
}

object PingCommand {
  def CommandName = "Ping"
}
