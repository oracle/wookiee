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

import com.webtrends.harness.component.socko.SockoService
import com.webtrends.harness.component.socko.route.SockoRouteManager
import com.webtrends.harness.component.spray.SprayService
import com.webtrends.harness.component.spray.directive.BaseDirectives
import com.webtrends.harness.component.spray.route.RouteManager
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.{GetMetaDetails, Ready}
import com.webtrends.harness.service.meta.ServiceMetaDetails
import org.joda.time.{DateTimeZone, DateTime}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class HttpExample extends SprayService with SockoService with BaseDirectives  {

  // add the example socko handler for this ping
  addHandler("SockoPing", new SockoPing(), Seq("GET"))

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands = {
    addCommand(PingCommand.CommandName, classOf[PingCommand])
    addCommand(SockoPingCommand.CommandName, classOf[SockoPingCommand])
  }

  /**
   * This is the receive expression
   *   Log when the Ready message is received
   */
  override def serviceReceive = ({
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
    case GetMetaDetails =>
      // you only need to handle this message in the case where you are mixing in two different
      // service traits like SprayService and SockoService, ordinarily you wouldn't do this, but
      // but this is an example
      //SockoRouteManager.addHandlers(handlers.toMap)
      RouteManager.addRoute(self.path.name, routes)
      sender() ! getMetaDetails
  }: Receive) //orElse runRoute(routes) //only need to add the runRoute service path because inheriting from two different services

  override def getMetaDetails() = {
    ServiceMetaDetails(handlers.size>0 || !routes.equals(Map.empty))
  }

  /**
   * Return the health of this service
   */
  override def getHealth: Future[HealthComponent] = {
    Future {
      HealthComponent("HttpExample", ComponentState.NORMAL, "HttpExample is running and ready to execute requests")
    }
  }

  /**
   * The routes to access information from this service
   *   /httpexample/ping returns "pong from httpexample: 2014-11-21T16:15:54.310Z"
   *   /httpexample/ping/json returns "{"message": "pong from httpexample", "time": "2014-11-21T15:46:43.006Z"}"
   */
  override def routes = {
    pathPrefix("httpexample") {
      get {
        pathPrefix("ping") {
          pathEndOrSingleSlash {
            respondPlain {
              ctx =>
                executeCommand[String](PingCommand.CommandName) onComplete {
                  case Success(s) => ctx.complete(s.data)
                  case Failure(f) => ctx.complete(f.getMessage)
                }
            }
          } ~
          path("json") {
            respondJson {
              val t = "".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString)
              complete( s""" {"message": "pong from httpexample", "time": "$t"} """)
            }
          }
        }
      }
    }
  }
}

