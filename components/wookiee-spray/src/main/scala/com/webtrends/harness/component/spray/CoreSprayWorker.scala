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
package com.webtrends.harness.component.spray

import _root_.spray.can.server.ServerSettings
import _root_.spray.http.{HttpRequest, HttpResponse, StatusCodes}
import _root_.spray.httpx.LiftJsonSupport
import _root_.spray.routing.directives.LogEntry
import _root_.spray.routing.{Rejected, Route, HttpServiceActor}
import akka.event.Logging
import akka.pattern.ask
import akka.routing.Broadcast
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.authentication.CIDRRules
import com.webtrends.harness.component._
import com.webtrends.harness.component.messages.{Rejoin, Subscriptions, ClusterState, StatusRequest}
import com.webtrends.harness.component.spray.directive.CIDRDirectives
import com.webtrends.harness.component.spray.route.RouteManager
import com.webtrends.harness.component.spray.serialization.EnumerationSerializer
import com.webtrends.harness.health.ComponentState
import com.webtrends.harness.health._
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.ServiceManager
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData
import com.webtrends.harness.service.meta.ServiceMetaData
import com.webtrends.harness.app.HarnessActor.ShutdownSystem
import com.webtrends.harness.app.Harness
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers
import org.joda.time.{DateTimeZone, DateTime}

import scala.util.{Success, Failure}

@SerialVersionUID(1L) case class HttpStartProcessing()
@SerialVersionUID(1L) case class HttpReloadRoutes()

class CoreSprayWorker extends HttpServiceActor
    with ActorHealth
    with ActorLoggingAdapter
    with CIDRDirectives
    with LiftJsonSupport
    with ComponentHelper {

  import context.dispatcher

  implicit val liftJsonFormats = net.liftweb.json.DefaultFormats + new EnumerationSerializer(ComponentState) ++ JodaTimeSerializers.all

  val spSettings = ServerSettings(context.system)
  var cidrRules:Option[CIDRRules] = Some(CIDRRules(context.system.settings.config))
  implicit val timeout = Timeout(spSettings.requestTimeout.toMillis)

  val healthActor = actorRefFactory.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = actorRefFactory.actorSelection(HarnessConstants.ServicesFullName)

  def receive: Receive = initializing

  /**
   * Establish our routes and other receive handlers
   */
  def initializing: Receive = health orElse runRoute(baseRoutes) orElse {
    case HttpStartProcessing => context.become(running)
    case HttpReloadRoutes => // Do nothing
  }

  def running: Receive = health orElse runRoute(logRequestResponse(myLog _) { getRoutes }) orElse {
    case HttpReloadRoutes =>
      context.become(initializing)
      self ! HttpStartProcessing
  }

  /**
   * Fetch routes from all registered services and concatenate with our default ones that
   * are defined below.
   */
  def getRoutes: Route = {
    val serviceRoutes = RouteManager.getRoutes.filter(r => !r.equals(Map.empty))
    (serviceRoutes ++ List(this.baseRoutes, this.staticRoutes)).reduceLeft(_ ~ _)
  }

  def baseRoutes = {
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
        path("ping") {
          respondPlain {
            complete("pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
          }
        } ~
        cidrFilter {
          pathPrefix("healthcheck") {
            path("lb") {
              respondPlain {
                complete((healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String])
              }
            } ~
            path("nagios") {
              //time(nagiosHealthTimer) {
                respondPlain {
                  complete((healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String])
                }
              //}
            } ~
            path("full") {
              //time(healthTimer) {
                respondJson {
                  complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
                }
              //}
            }

          } ~
            path("metrics") {
              respondJson {
                ctx =>
                  componentRequest[StatusRequest, JValue]("wookiee-metrics", ComponentRequest(StatusRequest())) onComplete {
                    case Success(s) => ctx.complete(s.resp)
                    case Failure(f) => ctx.failWith(f)
                  }
              }
            } ~
            pathPrefix("services") {
              pathEnd {
                respondJson {
                  complete((serviceActor ? GetMetaData(None)).mapTo[Seq[ServiceMetaData]])
                }
              } ~
                path(Segment) {
                  (service) =>
                    respondJson {
                      complete((serviceActor ? GetMetaDataByName(service)).mapTo[ServiceMetaData])
                    }
                }
            } ~
            pathPrefix("cluster") {
              pathEnd {
                respondJson {
                  ctx =>
                    val req = ComponentRequest(ClusterState(), Some("cluster"))
                    componentRequest[ClusterState, JValue]("wookiee-cluster", req) onComplete {
                      case Success(s) => ctx.complete(s.resp)
                      case Failure(f) => ctx.failWith(f)
                    }
                }
              } ~
                path("discovery") {
                  respondJson {
                    ctx =>
                      componentRequest[Subscriptions, JValue]("wookiee-cluster", ComponentRequest(Subscriptions())) onComplete {
                        case Success(s) => ctx.complete(s.resp)
                        case Failure(f) => ctx.failWith(f)
                      }
                  }
                }
            }
        }
    } ~
      post {
        cidrFilter {
          pathPrefix("services") {
            path(Segment / "restart") {
              (service) =>
                respondPlain {
                  ctx =>
                    serviceActor ! ServiceManager.RestartService(service)
                    ctx.complete(s"The service $service has been asked to restart")
                }
            }
          } ~
            path("shutdown") {
              respondPlain {
                ctx =>
                  ctx.complete("The system is being shutdown: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                  context.parent ! ShutdownSystem
              }
            } ~
            path("restart") {
              respondPlain {
                ctx =>
                  ctx.complete("The actor system is being restarted: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                  Harness.restartActorSystem
              }
            } ~
            pathPrefix("cluster") {
              path("rejoin") {
                respondPlain {
                  ctx =>
                    message("wookiee-cluster", ComponentMessage(Rejoin(true), Some("cluster")))
                    ctx.complete("The cluster is being rejoined: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                }
              }
            }
        }
      }
  }

  def staticRoutes = {
    val rootPath = context.system.settings.config.getString(SprayManager.KeyStaticRoot)
    context.system.settings.config.getString(SprayManager.KeyStaticType) match {
      case "file" =>
        getFromBrowseableDirectory(rootPath)
      case "jar" =>
        getFromResourceDirectory(rootPath)
      case _ =>
        getFromResourceDirectory(rootPath)
    }
  }

  def myLog(request: HttpRequest): Any => Option[LogEntry] = {
    case x: HttpResponse => {
      println (s"Normal: $request")
      createLogEntry(request,   x.status + " " + x.toString())
    }
    case Rejected(rejections) => {
      println (s"Rejection: $request")
      createLogEntry(request,   " Rejection " + rejections.toString())
    }
    case x => {
      println (s"other: $request")
      createLogEntry(request,   x.toString())
    }
  }

  def createLogEntry(request: HttpRequest, text: String): Some[LogEntry] = {
    Some(LogEntry("#### Request " + request + " => " + text, Logging.DebugLevel))
  }
}
