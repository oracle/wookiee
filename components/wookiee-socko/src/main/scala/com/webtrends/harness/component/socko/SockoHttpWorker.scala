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
import com.webtrends.harness.component.socko.route.SockoRouteManager
import com.webtrends.harness.component.{ComponentHelper, ComponentNotFoundException}
import com.webtrends.harness.health.{ComponentState, HealthRequest, HealthResponseType}
import com.webtrends.harness.utils.ConfigUtil
import io.netty.handler.codec.TooLongFrameException
import net.liftweb.json._
import net.liftweb.json.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.joda.time.{DateTime, DateTimeZone}
import org.mashupbots.socko.events.{HttpRequestEvent, HttpResponseStatus}
import org.mashupbots.socko.routes.{GET, Path}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class SockoWorkerMessage(event:HttpRequestEvent)

class SockoHttpWorker extends HActor with ComponentHelper {
  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val staticContent = ConfigUtil.getDefaultValue(SockoManager.KeyRootPaths, context.system.settings.config.getString, "")

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
    event match {
      case GET(Path("/favicon.ico")) =>
        event.response.write(HttpResponseStatus.NO_CONTENT)
      case GET(Path("/ping")) =>
        event.response.write("pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
      case GET(Path("/bad-request")) =>
        event.request.nettyHttpRequest.getDecoderResult.cause() match {
          case tl: TooLongFrameException => event.response.write(HttpResponseStatus.REQUEST_URI_TOO_LONG)
          case _ => event.response.write(HttpResponseStatus.BAD_REQUEST)
        }
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
      case _ =>
        val allHandlers = SockoRouteManager.getHandlers(event.nettyHttpRequest.getMethod.toString.toUpperCase)
        val matchedHandler = allHandlers match {
          case Some(handlers) =>
            try {
              handlers.find { handler =>
                handler._2.matchSockoRoute(event) match {
                  case Some(map) =>
                    Future {
                      handler._2.handleSockoRoute(event, map)
                    }
                    true
                  case None => false
                }
              }
            } catch {
              case ex: Exception =>
                log.error(s"Error matching path ${event.endPoint.path}", ex)
                None
            }
          case None => None
        }

        matchedHandler match {
          case Some(h) => // Handler already called
          case None => // Fallback to static content if initialized
            if (ConfigUtil.getDefaultValue(SockoManager.KeyRootPaths, context.system.settings.config.getString, "").nonEmpty) {
              context.actorSelection(s"${HarnessConstants.ComponentFullName}/harness-socko").resolveOne() onComplete {
                case Success(s) => s ! SockoContentRequest(event)
                case Failure(f) => event.response.write(HttpResponseStatus.NOT_FOUND)
              }
            } else {
              event.response.write(HttpResponseStatus.NOT_FOUND)
            }
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