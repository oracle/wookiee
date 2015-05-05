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

package com.webtrends.harness.component.socko

import akka.actor.{Props, ActorRef}
import com.webtrends.harness.command.{CommandManager, Command}
import com.webtrends.harness.component.socko.route.{SockoRouteManager, SockoRouteHandler}
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails

import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

/**
 * @author Michael Cuthbert on 1/26/15.
 */
trait SockoService extends Service {

  // list of currently held Socko Route handlers
  protected val handlers = mutable.Map[String, SockoRouteHandler]()

  def addHandler[T<:SockoRouteHandler](handlerName:String, handler:SockoRouteHandler, methods:Seq[String]) : Future[SockoRouteHandler] = {
    val p = Promise[SockoRouteHandler]
    if (classOf[Command].isAssignableFrom(handler.getClass)) {
      CommandManager.getCommand(handlerName) match {
        case Some(ref) => p success handler
        case None =>
          addCommand(handlerName, handler.getClass.asInstanceOf[Class[Command]]) onComplete {
            case Success(ref) =>
              handlers += handlerName -> handler
              SockoRouteManager.addHandlers(handlerName, handler, methods)
              p success handler
            case Failure(f) => p failure f
          }
      }
    } else {
      handlers += handlerName -> handler
      SockoRouteManager.addHandlers(handlerName, handler, methods)
      p success handler
    }
    p.future
  }

  // To be defined in service actor
  override def serviceReceive = {
    // This is called by the harness itself in order to get internal details of the service
    case GetMetaDetails =>
      // Get the services meta data
      sender() ! getMetaDetails
  }: Receive

  /**
   * The actor has been asked to respond with some additional meta information.
   * @return An instance of ServiceMetaDetails
   */
  protected def getMetaDetails: ServiceMetaDetails = {
    ServiceMetaDetails(handlers.size>0)
  }
}
