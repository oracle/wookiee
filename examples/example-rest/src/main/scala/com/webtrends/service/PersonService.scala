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

import akka.util.Timeout
import com.webtrends.harness.component.cache.{Cacheable, CreateCache, CacheConfig}
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.Ready
import com.webtrends.service.rest._
import com.webtrends.service.sockorest._
import spray.http.{MediaType, MediaTypes}
import scala.concurrent.duration._

import scala.util.{Failure, Success}

case class Person(name:String="None", age:Int=0) extends Cacheable[Person] {
  override def key: String = name
  override def namespace: String = PersonService.Namespace
}

class PersonService extends Service {

  implicit val timeout = Timeout(2 seconds)

  override def preStart = {
    super.preStart
    getComponent("wookiee-cache-memcache") onComplete {
      case Success(actor) =>
        val config = CacheConfig(
          namespace = PersonService.Namespace,
          props = Some(Map("serverList" -> "localhost:11211"))
        )
        actor ! CreateCache(config)
      case Failure(f) => throw f // in real world example might actually stop the service or something like that
    }

    // just testing remote commands, if HTTP example not running as well this will print out the exception
    executeCommand[String]("Ping", None, Some("127.0.0.1"), 5151) onComplete {
      case Success(s) => println(s.data.get)
      case Failure(f) => f.printStackTrace()
    }
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands = {
    addCommand(Create.CommandName, classOf[Create])
    addCommand(Read.CommandName, classOf[Read])
    addCommand(ReadJSON.CommandName, classOf[ReadJSON])
    addCommand(Update.CommandName, classOf[Update])
    addCommand(Delete.CommandName, classOf[Delete])
    addCommand(SCreate.CommandName, classOf[SCreate])
    addCommand(SRead.CommandName, classOf[SRead])
    addCommand(SReadJSON.CommandName, classOf[SReadJSON])
    addCommand(SUpdate.CommandName, classOf[SUpdate])
    addCommand(SDelete.CommandName, classOf[SDelete])
  }

  /**
   * This is the receive expression for your service. Apply any logic you wish
   * to handle specific messages
   */
  override def serviceReceive = ({
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
  }: Receive) orElse super.serviceReceive
}

object PersonService {
  val Namespace = "person-cache"

  val PersonMimeType = "application/vnd.webtrends.person"
  val `application/vnd.webtrends.person` = MediaTypes.register(MediaType.custom(PersonMimeType))
}

