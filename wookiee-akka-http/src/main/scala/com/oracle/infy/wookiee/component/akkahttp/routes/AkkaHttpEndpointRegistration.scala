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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, ExceptionHandler, RejectionHandler, Route}
import akka.stream.Supervision.Directive
import akka.stream.{Materializer, Supervision}
import ch.megard.akka.http.cors.javadsl
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.command.CommandHelper
import com.oracle.infy.wookiee.component.akkahttp.AkkaHttpManager
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.token.Error.UnauthorizedException.formats
import com.oracle.infy.wookiee.component.akkahttp.logging.AccessLog
import com.oracle.infy.wookiee.component.akkahttp.routes.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.akkahttp.routes.RouteGenerator._
import com.oracle.infy.wookiee.component.akkahttp.websocket.{AkkaHttpWebsocket, WebsocketInterface}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ConfigUtil
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.write

import java.util.concurrent.TimeUnit
import scala.reflect.runtime.universe._
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionException, Future}
import scala.reflect.ClassTag

object EndpointType extends Enumeration {
  type EndpointType = Value
  val INTERNAL, EXTERNAL, BOTH = Value
}

// These are all of the more optional bits of configuring an endpoint
case class EndpointOptions(
    accessLogIdGetter: AkkaHttpRequest => String = _ => "-",
    defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader],
    corsSettings: Option[CorsSettings] = None,
    routeTimerLabel: Option[String] = None,
    requestHandlerTimerLabel: Option[String] = None,
    businessLogicTimerLabel: Option[String] = None,
    responseHandlerTimerLabel: Option[String] = None
)

object EndpointOptions {
  val default: EndpointOptions = EndpointOptions()
}

trait AkkaHttpEndpointRegistration {
  this: CommandHelper with LoggingAdapter with HActor =>
  import AkkaHttpEndpointRegistration._

  private val accessLoggingEnabled =
    ConfigUtil.getDefaultValue(s"${AkkaHttpManager.ComponentName}.access-logging.enabled", config.getBoolean, true)
  if (accessLoggingEnabled) log.info("Access Logging Enabled") else log.info("Access Logging Disabled")

  def addAkkaHttpEndpoint[T <: Product: ClassTag: TypeTag, U: ClassTag: TypeTag](
      name: String,
      path: String,
      method: HttpMethod,
      endpointType: EndpointType.EndpointType,
      requestHandler: AkkaHttpRequest => Future[T],
      businessLogic: T => Future[U],
      responseHandler: U => Route,
      errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
      options: EndpointOptions = EndpointOptions.default
  )(
      implicit
      ec: ExecutionContext,
      responseTimeout: Option[FiniteDuration] = None,
      timeoutHandler: Option[HttpRequest => HttpResponse] = None
  ): Unit = {

    val sysTo = FiniteDuration(config.getDuration("akka.http.server.request-timeout").toNanos, TimeUnit.NANOSECONDS)
    val timeout = responseTimeout match {
      case Some(to) if to > sysTo =>
        log.warn(
          s"Time out of ${to.toMillis}ms for $method $path exceeds system max time out of ${sysTo.toMillis}ms."
        )
        sysTo
      case Some(to) => to
      case None     => sysTo
    }

    addCommand(name, businessLogic).foreach { ref =>
      val route = RouteGenerator
        .makeHttpRoute(
          path,
          method,
          ref,
          requestHandler,
          responseHandler,
          errorHandler,
          timeout,
          timeoutHandler.getOrElse(defaultTimeoutResponse),
          options,
          accessLoggingEnabled
        )

      addRoute(endpointType, route)
    }
  }

  /**
    * Main method used to register a websocket. Has native support for compression based on the 'Accept-Encoding' header
    * provided on the request. The given functions will be called in the order they are passed
    *
    * @param path Path to access this WS, can add query segments with '$', e.g. "/some/$param/in/path"
    * @param authHandler Handle auth here before request, if an exception is thrown it will bubble up to 'errorHandler'
    * @param textToInput Meant to convert a TextMessage into our main 'I' input type, will be provided with the auth object from 'authHandler'
    * @param handleInMessage Main business logic for this WS, takes input type 'I' and provides an WebsocketInterface
    *                        that allows one to reply as many times as desired for each input
    * @param outputToText Logic to parse output type 'O back to a TextMessage so it can be returned to client
    * @param onClose Logic to be called when a WS closes, good for resource cleanup, can be empty
    * @param authErrorHandler Handling logic in case an error is thrown during the 'authHandler' step
    * @param wsErrorHandler Handling logic in case an uncaught error is thrown during from the 'inputHandler' to the 'responseHandler'
    *                       steps, should return one of 'akka.stream.Supervision.Stop/Resume/Restart' based on the error
    * @param options Options for this endpoint, not all fields are used in the WS case
    *
    * @tparam A Class that can be used to hold auth information in case downstream requires it
    * @tparam I The main input type we should expect, will want to create a parser from TextMessage to it in 'inputHandler'
    * @tparam O The main output type we should expect, will want to parse it back to a TextMessage in 'responseHandler'
    */
  def addAkkaHttpWebsocket[I: ClassTag, O <: Product: ClassTag, A <: Product: ClassTag](
      path: String,
      endpointType: EndpointType.EndpointType,
      authHandler: AkkaHttpRequest => Future[A],
      textToInput: (A, TextMessage.Strict) => Future[I],
      handleInMessage: (I, WebsocketInterface[I, O, A]) => Unit,
      outputToText: O => TextMessage.Strict,
      onClose: (A, Option[I]) => Unit = { (_: A, _: Option[I]) =>
        ()
      },
      authErrorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route] = authErrorDefaultHandler,
      wsErrorHandler: PartialFunction[Throwable, Directive] = wsErrorDefaultHandler,
      options: EndpointOptions = EndpointOptions.default
  )(implicit ec: ExecutionContext, mat: Materializer): Unit = {
    addAkkaWebsocketEndpoint(
      path,
      endpointType,
      authHandler,
      textToInput,
      handleInMessage,
      outputToText,
      onClose,
      authErrorHandler,
      wsErrorHandler,
      options
    )
  }
}

object AkkaHttpEndpointRegistration extends LoggingAdapter {
  case class ErrorHolder(error: String)

  def defaultTimeoutResponse(request: HttpRequest): HttpResponse = {
    HttpResponse(
      StatusCodes.ServiceUnavailable,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""{"error": "${StatusCodes.ServiceUnavailable.defaultMessage}"}"""
      )
    )
  }

  // This works just as well as the corresponding method in the trait but doesn't require extending an actor to call
  def addAkkaWebsocketEndpoint[I: ClassTag, O <: Product: ClassTag, A <: Product: ClassTag](
      path: String,
      endpointType: EndpointType.EndpointType,
      authHandler: AkkaHttpRequest => Future[A],
      textToInput: (A, TextMessage.Strict) => Future[I],
      handleInMessage: (I, WebsocketInterface[I, O, A]) => Unit,
      outputToText: O => TextMessage.Strict,
      onClose: (A, Option[I]) => Unit = { (_: A, _: Option[I]) =>
        ()
      },
      authErrorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route] = authErrorDefaultHandler,
      wsErrorHandler: PartialFunction[Throwable, Directive] = wsErrorDefaultHandler,
      options: EndpointOptions = EndpointOptions.default
  )(implicit ec: ExecutionContext, mat: Materializer): Unit = {
    val httpPath = parseRouteSegments(path)
    val accessLogger = Some(options.accessLogIdGetter)
    val route = ignoreTrailingSlash {
      httpPath { segments: AkkaHttpPathSegments =>
        extractRequest { request =>
          parameterMap { paramMap: Map[String, String] =>
            val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
            respondWithHeaders(List()) {
              val locales = requestLocales(reqHeaders)
              val reqWrapper = AkkaHttpRequest(
                request.uri.path.toString,
                paramHoldersToList(segments),
                request.method,
                request.protocol,
                reqHeaders,
                paramMap,
                System.currentTimeMillis(),
                locales,
                None
              )

              corsWebsocketSupport(request.method, options.corsSettings, reqWrapper, accessLogger) {
                handleExceptions(ExceptionHandler({
                  case ex: ExecutionException =>
                    authErrorHandler(reqWrapper)(ex.getCause)
                  case t: Throwable =>
                    authErrorHandler(reqWrapper)(t)
                })) {
                  onSuccess(authHandler(reqWrapper)) { auth =>
                    val ws = new AkkaHttpWebsocket(
                      auth,
                      textToInput,
                      handleInMessage,
                      outputToText,
                      onClose,
                      wsErrorHandler,
                      options
                    )

                    handleWebSocketMessages(ws.websocketHandler(reqWrapper))
                  }
                }
              }
            }
          }
        }
      }
    }

    log.info(s"Adding Websocket on path $path to routes")
    addRoute(endpointType, route)
  }

  private def corsWebsocketSupport(
      method: HttpMethod,
      corsSettings: Option[CorsSettings],
      request: AkkaHttpRequest,
      accessLogIdGetter: Option[AkkaHttpRequest => String]
  ): Directive0 =
    corsSettings match {
      case Some(cors) =>
        handleRejections(corsWebSocketRejectionHandler(request, accessLogIdGetter)) &
          CorsDirectives.cors(cors.withAllowedMethods((cors.allowedMethods ++ immutable.Seq(method)).distinct))
      case None => pass
    }

  private def corsWebSocketRejectionHandler(
      request: AkkaHttpRequest,
      accessLogIdGetter: Option[AkkaHttpRequest => String]
  ): RejectionHandler = {
    RejectionHandler
      .newBuilder()
      .handleAll[javadsl.CorsRejection] { rejections =>
        val causes = rejections.map(_.cause.description).mkString(", ")
        accessLogIdGetter.foreach(g => AccessLog.logAccess(request, g(request), StatusCodes.Forbidden))
        complete(StatusCodes.Forbidden, write(ErrorHolder(causes)))
      }
      .result()
  }

  protected[oracle] def addRoute(endpointType: EndpointType, route: Route): Unit = {
    endpointType match {
      case EndpointType.INTERNAL =>
        InternalAkkaHttpRouteContainer.addRoute(route)
      case EndpointType.EXTERNAL =>
        ExternalAkkaHttpRouteContainer.addRoute(route)
      case EndpointType.BOTH =>
        ExternalAkkaHttpRouteContainer.addRoute(route)
        InternalAkkaHttpRouteContainer.addRoute(route)
    }
  }

  val authErrorDefaultHandler: AkkaHttpRequest => PartialFunction[Throwable, Route] = { req: AkkaHttpRequest =>
    {
      case err: Throwable =>
        implicit val formats: DefaultFormats.type = DefaultFormats
        log.warn(s"Error in processing Auth for request on path [${req.path}]", err)
        complete(write(ErrorHolder(err.getMessage)))
    }
  }

  val wsErrorDefaultHandler: PartialFunction[Throwable, Directive] = {
    case err: Throwable =>
      log.warn("Websocket message encountered unexpected error, skipping event", err)
      Supervision.Resume
  }
}
