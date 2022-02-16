/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.routes

import akka.actor.Props
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import com.oracle.infy.wookiee.component.akkahttp.ExternalAkkaHttpSettings
import org.joda.time.{DateTime, DateTimeZone}

object ExternalAkkaHttpActor {

  def props(settings: ExternalAkkaHttpSettings): Props = {
    Props(ExternalAkkaHttpActor(settings.port, settings.interface, settings.httpsPort, settings.serverSettings))
  }
}

case class ExternalAkkaHttpActor(port: Int, interface: String, httpsPort: Option[Int], settings: ServerSettings)
    extends AkkaHttpActor {

  val baseRoutes: Route =
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
        path("ping") {
          complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        }
    }

  override def routes: Route =
    if (ExternalAkkaHttpRouteContainer.isEmpty) {
      log.error("no routes defined")
      reject()
    } else {
      ExternalAkkaHttpRouteContainer.getRoutes.foldLeft(baseRoutes)(_ ~ _)
    }
}
