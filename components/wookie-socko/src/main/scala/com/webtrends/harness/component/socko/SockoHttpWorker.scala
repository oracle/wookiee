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

import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.messages.{ClusterState, StatusRequest, Subscriptions}
import com.webtrends.harness.component.socko.route.SockoRouteManager
import com.webtrends.harness.component.socko.utils.SockoCommandException
import com.webtrends.harness.component.{ComponentHelper, ComponentNotFoundException, ComponentRequest}
import com.webtrends.harness.health.{ApplicationHealth, ComponentState, HealthRequest, HealthResponseType}
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData
import com.webtrends.harness.service.meta.ServiceMetaData
import com.webtrends.harness.utils.ConfigUtil
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.json.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.joda.time.{DateTime, DateTimeZone}
import org.mashupbots.socko.events.{HttpRequestEvent, HttpResponseStatus}
import org.mashupbots.socko.routes.{GET, Path, PathSegments}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

case class SockoWorkerMessage(event:HttpRequestEvent)

class SockoHttpWorker extends HActor with ComponentHelper {
  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val staticContent = ConfigUtil.getDefaultValue(SockoManager.KeyRootPaths, context.system.settings.config.getString, "")
  val allowHealthCheck = ConfigUtil.getDefaultValue(SockoManager.AllowFullHealthCheck, context.system.settings.config.getBoolean, true)

  implicit val formats = DefaultFormats + new EnumNameSerializer(ComponentState) ++ JodaTimeSerializers.all

  val healthActor = context.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = context.actorSelection(HarnessConstants.ServicesFullName)

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    self ! HttpStartProcessing
  }

  override def receive: Receive = {
    case HttpStartProcessing =>
      log.debug("Worker beginning to process")
      context.become(running)
  }

  def running:Receive = super.receive orElse {
    case SockoWorkerMessage(event) => handleSockoRoute(event)
  }

  /**
   * Attempts to handle the Socko route
   * @param event The http request
   */
  protected def handleSockoRoute(event: HttpRequestEvent) = {
    var handled = true
    event match {
      case GET(Path("/favicon.ico")) =>
        event.response.write(HttpResponseStatus.NO_CONTENT)
      case GET(Path("/ping")) =>
        event.response.write("pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
      case req =>
        req match {
          case GET(Path("/healthcheck/lb")) =>
            (healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String] onComplete {
              case Success(h) => event.response.write(h)
              case Failure(f) => messageFailure(f, event)
            }
          case GET(Path("/healthcheck/nagios")) =>
            (healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String] onComplete {
              case Success(h) => event.response.write(h)
              case Failure(f) => messageFailure(f, event)
            }
          case GET(Path("/healthcheck/full")) if allowHealthCheck =>
            (healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth] onComplete {
              case Success(h) => event.response.write(compactRender(decompose(h)))
              case Failure(f) => messageFailure(f, event)
            }
          case GET(Path("/metrics")) if allowHealthCheck =>
            componentRequest[StatusRequest, JValue]("wookie-metrics", ComponentRequest(StatusRequest())) onComplete {
              case Success(s) => event.response.write(JsonAST.compactRender(s.resp))
              case Failure(f) => messageFailure(f, event)
            }
          case GET(PathSegments("services" :: relativePath)) if allowHealthCheck =>
            if (relativePath.size == 0 || relativePath(0).equals("")) {
              (serviceActor ? GetMetaData(None)).mapTo[Seq[ServiceMetaData]] onComplete {
                case Success(s) => event.response.write(compactRender(decompose(s)))
                case Failure(f) => messageFailure(f, event)
              }
            } else {
              (serviceActor ? GetMetaDataByName(relativePath(0))).mapTo[Seq[ServiceMetaData]] onComplete {
                case Success(s) => event.response.write(compactRender(decompose(s)))
                case Failure(f) => messageFailure(f, event)
              }
            }
          case GET(Path("/cluster")) if allowHealthCheck =>
            val req = ComponentRequest(ClusterState(), Some("cluster"))
            componentRequest[ClusterState, JValue]("wookie-cluster", req) onComplete {
              case Success(s) => event.response.write(compactRender(s.resp))
              case Failure(f) => messageFailure(f, event)
            }
          case GET(Path("/cluster/discovery")) if allowHealthCheck =>
            componentRequest[Subscriptions, JValue]("wookie-cluster", ComponentRequest(Subscriptions())) onComplete {
              case Success(s) => event.response.write(compactRender(s.resp))
              case Failure(f) => messageFailure(f, event)
            }
          case _ =>
            var found = false
            SockoRouteManager.getHandlers(event.nettyHttpRequest.getMethod.toString.toUpperCase) match {
              case Some(handlers) =>
                try {
                  handlers.takeWhile { handler =>
                    handler._2.matchSockoRoute(event) match {
                      case Some(map) =>
                        Future {
                          handler._2.handleSockoRoute(event, map)
                        }
                        found = true
                      case None =>
                    }
                    !found
                  }
                } catch {
                  case ce: SockoCommandException => event.response.write(ce.code, s"Cause - ${ce.getMessage}")
                  case ex: Exception => event.response.write(HttpResponseStatus.INTERNAL_SERVER_ERROR, s"Cause - ${ex.getMessage}")
                }
              case None =>
            }
            handled = found
        }
    }

    if (!handled) {
      //if the static content handler was initialized then lets send the message out to the content handler
      if (ConfigUtil.getDefaultValue(SockoManager.KeyRootPaths, context.system.settings.config.getString, "").nonEmpty) {
        context.actorSelection(s"${HarnessConstants.ComponentFullName}/wookie-socko").resolveOne() onComplete {
          case Success(s) => s ! SockoContentRequest(event)
          case Failure(f) => event.response.write(HttpResponseStatus.NOT_FOUND)
        }
      } else {
        event.response.write(HttpResponseStatus.NOT_FOUND)
      }
    }
  }

  def messageFailure(f:Throwable, event:HttpRequestEvent) = {
    f match {
      case notFound:ComponentNotFoundException => event.response.write(HttpResponseStatus.NOT_FOUND, notFound.getMessage)
      case t => event.response.write(HttpResponseStatus.INTERNAL_SERVER_ERROR, t.getMessage)
    }
  }
}
