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

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  Origin,
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Origin`,
  `Access-Control-Expose-Headers`
}
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import ch.megard.akka.http.cors.javadsl
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import scala.reflect.runtime.universe._
import com.oracle.infy.wookiee.command.ExecuteCommand
import com.oracle.infy.wookiee.component.akkahttp.logging.AccessLog
import com.oracle.infy.wookiee.component.metrics.TimerStopwatch
import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.util.Locale
import java.util.Locale.LanguageRange
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

case class AkkaHttpRequest(
    path: String,
    // List, not Seq as Seq is not <: Product and route params are common command inputs
    segments: List[String],
    method: HttpMethod,
    protocol: HttpProtocol,
    requestHeaders: Map[String, String],
    queryParams: Map[String, String],
    time: Long,
    locales: List[Locale],
    requestBody: Option[RequestEntity] = None
)

object RouteGenerator extends LoggingAdapter {

  def makeHttpRoute[T <: Product: ClassTag: TypeTag, V](
      path: String,
      method: HttpMethod,
      commandRef: ActorRef,
      requestHandler: AkkaHttpRequest => Future[T],
      responseHandler: V => Route,
      errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
      responseTimeout: FiniteDuration,
      timeoutHandler: HttpRequest => HttpResponse,
      options: EndpointOptions = EndpointOptions.default,
      accessLoggingEnabled: Boolean = false
  )(implicit ec: ExecutionContext): Route = {
    val accessLogger = if (accessLoggingEnabled) Some(options.accessLogIdGetter) else None
    val httpPath = parseRouteSegments(path)
    ignoreTrailingSlash {
      httpPath { segments: AkkaHttpPathSegments =>
        respondWithHeaders(options.defaultHeaders.toList) {
          parameterMap { paramMap: Map[String, String] =>
            val routeTimer = options.routeTimerLabel.map(TimerStopwatch(_))
            extractRequest { request =>
              val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
              val httpEntity = getPayload(method, request)
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
                httpEntity
              )
              corsSupport(method, options.corsSettings, reqWrapper, accessLogger) {
                httpMethod(method) {
                  // Timeout behavior requires that CORS has already been enforced
                  withRequestTimeout(
                    responseTimeout,
                    req => timeoutCors(req, timeoutHandler(req), options.corsSettings)
                  ) {
                    // http request handlers should be built with authorization in mind.
                    implicit val akkaTimeout: Timeout = responseTimeout
                    onComplete(
                      (for {
                        requestObjs <- maybeTimeF(options.requestHandlerTimerLabel, { requestHandler(reqWrapper) })
                        commandResult <- maybeTimeF(options.businessLogicTimerLabel, {
                          commandRef ? ExecuteCommand("", requestObjs, responseTimeout)
                        })
                      } yield maybeTime(options.responseHandlerTimerLabel, {
                        responseHandler(commandResult.asInstanceOf[V])
                      }))
                        .recover(errorHandler(reqWrapper))
                    ) {
                      case Success(route: Route) =>
                        mapRouteResult {
                          case Complete(response) =>
                            accessLogger.foreach(g => AccessLog.logAccess(reqWrapper, g(reqWrapper), response.status))
                            routeTimer.foreach(finishTimer(_, response.status.intValue))
                            Complete(response)
                          case Rejected(_) =>
                            // TODO: Current expectation is that user's errorHandler should already handle rejections before this point
                            ???
                        }(route)
                      case Failure(ex: Throwable) =>
                        val firstClass = ex
                          .getStackTrace
                          .headOption
                          .map(_.getClassName)
                          .getOrElse(ex.getClass.getSimpleName)
                        log.warn(
                          s"Unhandled Error [$firstClass - '${ex.getMessage}'], update rejection handlers for path: $path",
                          ex
                        )
                        accessLogger.foreach(
                          g => AccessLog.logAccess(reqWrapper, g(reqWrapper), StatusCodes.InternalServerError)
                        )
                        routeTimer.foreach(finishTimer(_, StatusCodes.InternalServerError.intValue))
                        complete(StatusCodes.InternalServerError, "There was an internal server error.")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private def maybeTime[T](timerLabel: Option[String], toRun: => T): T = {
    timerLabel match {
      case Some(label) =>
        TimerStopwatch.tryWrapper(label)({ toRun })
      case None =>
        toRun
    }
  }

  private def maybeTimeF[T](timerLabel: Option[String], toRun: => Future[T])(
      implicit ec: ExecutionContext
  ): Future[T] = {
    timerLabel match {
      case Some(label) =>
        TimerStopwatch.futureWrapper(label)({ toRun })
      case None =>
        toRun
    }
  }

  // At this point in the route, the CORS directive has already been applied, meaning that pre-flight requests have
  // already been handled and any invalid requests have  been rejected.
  // Rather than re-apply the CORS logic here (which isn't accessible) we simply echo back the request origin to allow
  // clients to handle the timeout response correctly
  private def timeoutCors(request: HttpRequest, response: HttpResponse, cors: Option[CorsSettings]): HttpResponse = {
    response.withHeaders(
      response.headers ++ ((cors, request.header[Origin]) match {
        case (Some(c), Some(o)) =>
          List(
            Some(`Access-Control-Allow-Origin`(o.origins.head)),
            if (c.allowCredentials) Some(`Access-Control-Allow-Credentials`(true)) else None,
            if (c.exposedHeaders.nonEmpty) Some(`Access-Control-Expose-Headers`(c.exposedHeaders)) else None
          ).flatten
        case _ =>
          List()
      })
    )
  }

  // Use these to generically extract values from a query string
  private case class Holder1(_1: String) extends Product1[String] with AkkaHttpPathSegments
  private case class Holder2(_1: String, _2: String) extends Product2[String, String] with AkkaHttpPathSegments

  private case class Holder3(_1: String, _2: String, _3: String)
      extends Product3[String, String, String]
      with AkkaHttpPathSegments

  private case class Holder4(_1: String, _2: String, _3: String, _4: String)
      extends Product4[String, String, String, String]
      with AkkaHttpPathSegments

  private case class Holder5(_1: String, _2: String, _3: String, _4: String, _5: String)
      extends Product5[String, String, String, String, String]
      with AkkaHttpPathSegments

  private case class Holder6(_1: String, _2: String, _3: String, _4: String, _5: String, _6: String)
      extends Product6[String, String, String, String, String, String]
      with AkkaHttpPathSegments

  type Path0 = PathMatcher[Unit]
  type Path1 = PathMatcher[Tuple1[String]]
  type Path2 = PathMatcher[(String, String)]
  type Path3 = PathMatcher[(String, String, String)]
  type Path4 = PathMatcher[(String, String, String, String)]
  type Path5 = PathMatcher[(String, String, String, String, String)]
  type Path6 = PathMatcher[(String, String, String, String, String, String)]

  // Don't expose this, just use the paramHoldersToList to change it to something actually useful.
  protected[akkahttp] def parseRouteSegments(path: String): Directive1[AkkaHttpPathSegments] = {
    val segs = path.split("/").filter(_.nonEmpty).toSeq
    var segCount = 0
    try {
      val dir = segs.tail.foldLeft(segs.head.asInstanceOf[Any]) { (x, y) =>
        y match {
          case s1: String if s1.startsWith("$") =>
            segCount += 1
            (x match {
              case pStr: String =>
                if (pStr.startsWith("$")) {
                  segCount += 1
                  Segment / Segment
                } else pStr / Segment
              case pMatch: PathMatcher[_] if segCount == 1 => pMatch.asInstanceOf[Path0] / Segment
              case pMatch: PathMatcher[_] if segCount == 2 => pMatch.asInstanceOf[Path1] / Segment
              case pMatch: PathMatcher[_] if segCount == 3 => pMatch.asInstanceOf[Path2] / Segment
              case pMatch: PathMatcher[_] if segCount == 4 => pMatch.asInstanceOf[Path3] / Segment
              case pMatch: PathMatcher[_] if segCount == 5 => pMatch.asInstanceOf[Path4] / Segment
              case pMatch: PathMatcher[_] if segCount == 6 => pMatch.asInstanceOf[Path5] / Segment

            }).asInstanceOf[PathMatcher[_]]
          case s1: String =>
            (x match {
              case pStr: String =>
                if (pStr.startsWith("$")) {
                  segCount += 1
                  Segment / s1
                } else pStr / s1
              case pMatch: PathMatcher[_] => pMatch / s1
            }).asInstanceOf[PathMatcher[_]]
        }
      }

      // Create holders for any arguments on the query path
      segCount match {
        case 0 if segs.size == 1 => p(path) & provide(new AkkaHttpPathSegments {})
        case 0                   => p(dir.asInstanceOf[Path0]) & provide(new AkkaHttpPathSegments {})
        case 1                   => p(dir.asInstanceOf[Path1]).as(Holder1)
        case 2                   => p(dir.asInstanceOf[Path2]).as(Holder2)
        case 3                   => p(dir.asInstanceOf[Path3]).as(Holder3)
        case 4                   => p(dir.asInstanceOf[Path4]).as(Holder4)
        case 5                   => p(dir.asInstanceOf[Path5]).as(Holder5)
        case 6                   => p(dir.asInstanceOf[Path6]).as(Holder6)
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Error adding path $path", ex)
        throw ex
    }
  }

  private[akkahttp] def paramHoldersToList(segments: AkkaHttpPathSegments): List[String] =
    segments match {
      case Holder1(a)                => List(a)
      case Holder2(a, b)             => List(a, b)
      case Holder3(a, b, c)          => List(a, b, c)
      case Holder4(a, b, c, d)       => List(a, b, c, d)
      case Holder5(a, b, c, d, e)    => List(a, b, c, d, e)
      case Holder6(a, b, c, d, e, f) => List(a, b, c, d, e, f)
      case _                         => List()
    }

  def getPayload(method: HttpMethod, request: HttpRequest): Option[RequestEntity] = method match {
    case HttpMethods.PUT | HttpMethods.POST | HttpMethods.PATCH | HttpMethods.DELETE => Some(request.entity)
    case _                                                                           => None
  }

  private def corsSupport(
      method: HttpMethod,
      corsSettings: Option[CorsSettings],
      request: AkkaHttpRequest,
      accessLogIdGetter: Option[AkkaHttpRequest => String]
  ): Directive0 =
    corsSettings match {
      case Some(cors) =>
        handleRejections(corsRejectionHandler(request, accessLogIdGetter)) &
          CorsDirectives.cors(cors.withAllowedMethods((cors.allowedMethods ++ immutable.Seq(method)).distinct))
      case None => pass
    }

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET     => get
    case HttpMethods.PUT     => put
    case HttpMethods.POST    => post
    case HttpMethods.DELETE  => delete
    case HttpMethods.OPTIONS => options
    case HttpMethods.PATCH   => patch
    case HttpMethods.HEAD    => head
    case _                   => options
  }

  def requestLocales(headers: Map[String, String]): List[Locale] =
    headers.get("accept-language") match {
      case Some(localeString) if localeString.nonEmpty =>
        LanguageRange
          .parse(localeString)
          .asScala
          .map(language => Locale.forLanguageTag(language.getRange))
          .toList
      case _ => Nil
    }

  private def corsRejectionHandler(
      request: AkkaHttpRequest,
      accessLogIdGetter: Option[AkkaHttpRequest => String]
  ): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handleAll[javadsl.CorsRejection] { rejections =>
        val causes = rejections.map(_.cause.description).mkString(", ")
        accessLogIdGetter.foreach(g => AccessLog.logAccess(request, g(request), StatusCodes.Forbidden))
        complete((StatusCodes.Forbidden, s"CORS: $causes"))
      }
      .result()

  private def finishTimer(timer: TimerStopwatch, statusCode: Int): Unit =
    statusCode match {
      case n if n >= 200 && n < 400 => timer.success()
      case n if n >= 400 && n < 500 => timer.failure("request")
      case _                        => timer.failure("server")
    }

}
