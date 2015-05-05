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

import java.nio.charset.StandardCharsets

import com.webtrends.harness.command.{CommandResponse, CommandBean, Command}
import com.webtrends.harness.component.socko.route._
import com.webtrends.harness.component.socko.utils.SockoCommandBean
import net.liftweb.json.JsonParser
import org.joda.time.{DateTimeZone, DateTime}
import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.routes.{GET, Path}

import scala.collection.mutable
import scala.concurrent.Future

case class Foo(bar:String)

/**
 * @author Michael Cuthbert on 1/28/15.
 */
class SockoPingCommand extends Command
    // SOCKO traits ---------
    with SockoCustom
    with SockoGet
    with SockoPost
    with SockoHead
    with SockoPut
    with SockoOptions
    with SockoDelete {
  implicit val executionContext = context.dispatcher
  override def path: String = "/socko/test/$key/ping"



  override def customHandler(event:HttpRequestEvent, bean: mutable.HashMap[String, AnyRef]): Unit = {
    innerExecute(new SockoCommandBean(event))
  }

  override def customMatcher(event: HttpRequestEvent): Option[mutable.HashMap[String, AnyRef]] = {
    event match {
      case GET(Path("/socko/customping")) => Some(new mutable.HashMap[String, AnyRef]())
      case _ => None
    }
  }

  override def unmarshall[T <: AnyRef : Manifest](obj: Array[Byte], contentType: String = "application/json"): Option[T] = {
    if (obj == null || obj.length == 0) {
      None
    } else {
      Some(JsonParser.parse(new String(obj, StandardCharsets.UTF_8)).extract[Foo].asInstanceOf[T])
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
          b.appendMap(Map("socko pong" -> dtime))
          CommandResponse(Some(b.toMap.asInstanceOf[T]), "json")
        case None =>
          CommandResponse(Some("socko pong from httpexample: ".concat(dtime).asInstanceOf[T]), "txt")
      }
    }
  }
}

object SockoPingCommand {
  def CommandName = "SockoPing"
}