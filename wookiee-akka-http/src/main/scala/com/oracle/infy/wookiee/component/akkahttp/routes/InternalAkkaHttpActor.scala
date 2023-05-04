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

import akka.actor.{ActorSelection, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import akka.pattern.ask
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.Harness
import com.oracle.infy.wookiee.component.akkahttp.{AkkaHttpManager, InternalAkkaHttpSettings}
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.{ComponentHelper, ComponentRequest}
import com.oracle.infy.wookiee.health.{ApplicationHealth, ComponentState, HealthRequest, HealthResponseType}
import com.oracle.infy.wookiee.service.ServiceManager
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, JValue, jackson}

import scala.util.{Failure, Success}

object InternalAkkaHttpActor {

  def props(settings: InternalAkkaHttpSettings): Props = {
    Props(InternalAkkaHttpActor(settings.port, settings.interface, settings.httpsPort, settings.serverSettings))
  }
}

case class AkkaHttpUnbind()

case class InternalAkkaHttpActor(port: Int, interface: String, httpsPort: Option[Int], settings: ServerSettings)
    extends AkkaHttpActor
    with ComponentHelper {
  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: Formats = (DefaultFormats ++ JodaTimeSerializers.all) + new EnumNameSerializer(ComponentState)

  val healthActor: ActorSelection = system.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor: ActorSelection = system.actorSelection(HarnessConstants.ServicesFullName)

  val baseRoutes: Route =
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
        path("ping") {
          complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        } ~
        path("healthcheck") {
          complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
        } ~
        pathPrefix("healthcheck") {
          path("lb") {
            complete((healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String])
          } ~
            path("nagios") {
              completeOrRecoverWith((healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String]) { failWith }
            } ~
            path("full") {
              complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
            }
        } ~
        path("metrics") {
          onComplete(componentRequest[StatusRequest, JValue]("wookiee-metrics", ComponentRequest(StatusRequest()))) {
            case Success(s) => complete(s.resp)
            case Failure(f) => failWith(f)
          }
        }
    } ~ post {
      pathPrefix("services") {
        path(Segment / "restart") { service =>
          serviceActor ! ServiceManager.RestartService()
          complete(s"The service $service has been asked to restart")
        }
      } ~
        path("shutdown") {
          Harness.shutdown()(system)
          complete(s"The system is being shutdown: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        } ~
        path("restart") {
          Harness.restartActorSystem()(system)
          complete(s"The system is being restarted: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        }
    }

  def staticRoutes: Route = {
    val rootPath = config.getString(AkkaHttpManager.KeyStaticRoot)
    config.getString(AkkaHttpManager.KeyStaticType) match {
      case "file" =>
        getFromBrowseableDirectory(rootPath)
      case "jar" =>
        getFromResourceDirectory(rootPath)
      case _ =>
        getFromResourceDirectory(rootPath)
    }
  }

  override def routes: Route =
    InternalAkkaHttpRouteContainer
      .getRoutes
      .foldLeft(
        ExternalAkkaHttpRouteContainer
          .getRoutes
          .foldLeft(baseRoutes ~ staticRoutes)(_ ~ _)
      )(_ ~ _)
}
