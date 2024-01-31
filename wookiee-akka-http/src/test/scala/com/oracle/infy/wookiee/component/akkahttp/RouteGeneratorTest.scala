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

package com.oracle.infy.wookiee.component.akkahttp

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model.HttpHeader.ParsingResult.{Error, Ok}
import akka.http.scaladsl.model.headers.{
  HttpOrigin,
  Origin,
  `Access-Control-Allow-Methods`,
  `Access-Control-Request-Method`
}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.oracle.infy.wookiee.command.CommandFactory
import com.oracle.infy.wookiee.component.akkahttp.util.TestJsonSupport._
import com.oracle.infy.wookiee.component.akkahttp.routes.{
  AkkaHttpEndpointRegistration,
  AkkaHttpRequest,
  EndpointOptions,
  RouteGenerator
}
import com.oracle.infy.wookiee.component.akkahttp.util.{Forbidden, NotAuthorized, RequestInfo}
import com.oracle.infy.wookiee.logging.Logger
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Locale
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class RouteGeneratorTest extends AnyWordSpecLike with ScalatestRouteTest with PredefinedToEntityMarshallers {

  implicit val actorSystem: ActorSystem = ActorSystem("route-test")
  implicit val logger: Logger = Logger.getLogger(getClass.getName)
  val responseTo: FiniteDuration = FiniteDuration(10, TimeUnit.MILLISECONDS)
  val toHandler: HttpRequest => HttpResponse = AkkaHttpEndpointRegistration.defaultTimeoutResponse
  val defaultConfig: EndpointOptions = EndpointOptions.default

  import RouteGeneratorTest._

  def actorRef: ActorRef = actorSystem.actorOf(CommandFactory.createCommand(simpleFunction))
  def messageActorRef: ActorRef = actorSystem.actorOf(CommandFactory.createCommand(messageFunction))
  def exceptionActorRef: ActorRef = actorSystem.actorOf(CommandFactory.createCommand(exceptionFunction))

  "RouteGenerator " should {

    "add simple route" in {
      val r = RouteGenerator.makeHttpRoute(
        "getTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/getTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "getTest")
      }
    }
    "ignore trailing slash" in {
      val r = RouteGenerator.makeHttpRoute(
        "getTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/getTest/") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "getTest")
      }
    }
    "route with path segments" in {
      val r = RouteGenerator.makeHttpRoute(
        "getTest/$id",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/getTest/123") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].segments == List("123"))
      }
    }
    "route with path segments and query params" in {
      val r = RouteGenerator.makeHttpRoute(
        "getTest/$id",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/getTest/123?enable=true") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].segments == List("123"))
        assert(entityAs[RequestInfo].queryParams == Map("enable" -> "true"))
      }
    }
    "route with post method" in {
      val r = RouteGenerator.makeHttpRoute(
        "postTest",
        HttpMethods.POST,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Post("/postTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].verb == "HttpMethod(POST)")
      }
    }

    "providing accessLog id getter gets called with AkkaHttpRequest object" in {
      var called = false
      val r = RouteGenerator.makeHttpRoute(
        "accessLogTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        defaultConfig.copy(accessLogIdGetter = r => {
          r match {
            case _: AkkaHttpRequest => called = true
            case _                  =>
          }
          "works"
        }),
        accessLoggingEnabled = true
      )

      Get("/accessLogTest") ~> r ~> check {
        assert(called, "called variable not reset by accessLogIdGetter call")
      }
    }

    "response should include defaults headers" in {
      val userHeader = HttpHeader.parse("userId", "system") match {
        case Ok(header, _) => Some(header)
        case Error(_)      => None
      }
      val r = RouteGenerator.makeHttpRoute(
        "getTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        defaultConfig.copy(defaultHeaders = Seq(userHeader.get))
      )

      Get("/getTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(headers.contains(userHeader.get))
      }
    }

    "deal with odd weight values in locale" in {
      val locale = RouteGenerator.requestLocales(Map("accept-language" -> "en;q=0.8")).head
      assert(locale.getLanguage.equals("en"))
      val locale2 = RouteGenerator.requestLocales(Map("accept-language" -> "en-US,en;weight=0.8;q=0.9"))
      assert(locale2.isEmpty)
    }

    "route with Delete method without request body" in {
      val r = RouteGenerator.makeHttpRoute(
        "deleteTest",
        HttpMethods.DELETE,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Delete("/deleteTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].body.contains(""))
      }
    }

    "route with Delete method having request body" in {
      val r = RouteGenerator.makeHttpRoute(
        "deleteTest",
        HttpMethods.DELETE,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Delete("/deleteTest", "Welcome to Wookiee-Akka-Http") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].body.contains("Welcome to Wookiee-Akka-Http"))
      }
    }
  }

  "Request Handler" should {
    "process authentication logic" in {
      var isAuthenticated = false
      val r = RouteGenerator.makeHttpRoute("authTest", HttpMethods.GET, actorRef, r => {
        isAuthenticated = true; Future.successful(r)
      }, responseHandler200, rejectionHandler, responseTo, toHandler)

      Get("/authTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(isAuthenticated, "authentication not called")
      }
    }
    "route with authentication failure returned in request handler throw 401 error code" in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        actorRef,
        requestHandlerWithAuthenticationFailure,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.Unauthorized)
        assert(entityAs[String] contains failMessage)
      }
    }
    "route with unknown failure returned in request handler throw 500 error code " in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        actorRef,
        requestHandlerWithUnknownFailure,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        assert(entityAs[String] contains failMessage)
      }
    }
    "route with exception in request handler (abnormal termination of logic) throw 500 error code " in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        actorRef,
        requestHandlerWithException,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        // exceptions are caught by default exception handler of Akka Http
        assert(entityAs[String] contains StatusCodes.InternalServerError.defaultMessage)
      }
    }
  }

  "Response Handler" should {
    "successfully transform output of the business logic into Akka Http Route" in {
      val r = RouteGenerator.makeHttpRoute(
        "account/$accountGuid/report/$reportId",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/account/abc/report/123") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[RequestInfo].segments == List("abc", "123"))
      }
    }
    "route with error in response handler" in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        errorOnResponse,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        assert(entityAs[String] contains failMessage)
      }
    }
    "actorRef output and response handler input should of same type, otherwise throw cast exception" in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        messageActorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        assert(entityAs[String] contains "cannot be cast to")
      }
    }
  }

  "Rejection Handler" should {
    "catch failure in the request handler " in {
      val r = RouteGenerator.makeHttpRoute("authTest", HttpMethods.GET, actorRef, _ => {
        Future.failed(Forbidden(failMessage))
      }, responseHandler200, rejectionHandler, responseTo, toHandler)

      Get("/authTest") ~> r ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(entityAs[String] contains failMessage)
      }
    }
    "must caught Failure in business logic(actorRef)" in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        exceptionActorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        assert(entityAs[String] contains failMessage)
      }
    }
    "If an exception is not handle in rejection handler then it must be caught at onComplete Failure case" in {
      val r = RouteGenerator.makeHttpRoute(
        "errorTest",
        HttpMethods.GET,
        exceptionActorRef,
        requestHandler,
        responseHandler200,
        rejectionHandlerWithLimitedScope,
        responseTo,
        toHandler
      )
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.InternalServerError)
        assert(entityAs[String] contains StatusCodes.InternalServerError.defaultMessage)
      }
    }
  }

  "Request Locales" should {
    "accept-language header with multiple languages" in {
      val locales = RouteGenerator.requestLocales(Map("accept-language" -> "ru;q=0.9, de, en;q=0.7"))
      assert(locales.size == 3)
      assert(locales.head == Locale.forLanguageTag("de"))
      assert(locales(1) == Locale.forLanguageTag("ru"))
      assert(locales(2) == Locale.forLanguageTag("en"))
    }
    "accept-language header with empty string" in {
      val locales = RouteGenerator.requestLocales(Map("accept-language" -> ""))
      assert(locales.isEmpty)
    }
    "requestLocales with out accept-language header" in {
      val locales = RouteGenerator.requestLocales(Map.empty)
      assert(locales.isEmpty)
    }
  }

  "Cors settings" should {
    val whiteListOrigin = HttpOrigin("http://example.com")
    val corsSettings = defaultConfig.copy(
      corsSettings = Some(
        CorsSettings
          .defaultSettings
          .withAllowedMethods(List())
          .withAllowedOrigins(HttpOriginMatcher(whiteListOrigin))
      )
    )
    "Allows a request with whitelisted origin" in {
      val r = RouteGenerator.makeHttpRoute(
        "corsTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        corsSettings
      )
      Get("/corsTest") ~> Origin(whiteListOrigin) ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "corsTest")
      }
    }
    "Forbidden the request with unknown origin" in {
      val r = RouteGenerator.makeHttpRoute(
        "corsTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        corsSettings
      )
      Get("/corsTest") ~> Origin(HttpOrigin("http://unknown.com")) ~> r ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(entityAs[String] contains "CORS: invalid origin 'http://unknown.com'")
      }
    }
    "Route with No Cors settings  must allow any origin" in {
      val r = RouteGenerator.makeHttpRoute(
        "corsTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler
      )
      Get("/corsTest") ~> Origin(HttpOrigin("http://unknown.com")) ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "corsTest")
      }
    }
    "pre flight allow the request with whitelist origin" in {
      val r = RouteGenerator.makeHttpRoute(
        "corsTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        corsSettings
      )
      Options("/corsTest") ~> Origin(HttpOrigin("http://example.com")) ~> `Access-Control-Request-Method`(
        HttpMethods.GET
      ) ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(response.headers contains `Access-Control-Allow-Methods`(List(HttpMethods.GET)))
      }
    }
    "pre flight should Forbidden the request with unknown origin" in {
      val r = RouteGenerator.makeHttpRoute(
        "corsTest",
        HttpMethods.GET,
        actorRef,
        requestHandler,
        responseHandler200,
        rejectionHandler,
        responseTo,
        toHandler,
        corsSettings
      )
      Options("/corsTest") ~> Origin(HttpOrigin("http://unknown.com")) ~> `Access-Control-Request-Method`(
        HttpMethods.GET
      ) ~> r ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(entityAs[String] contains "CORS: invalid origin 'http://unknown.com'")
      }
    }
  }

  // Testing timeout behavior requires running tests in full server mode (using ~!>) which is slow/expensive
  /*"Timeout behavior" should {
    "Use specified timeout and timeout handler" in {
      val r = RouteGenerator.makeHttpRoute(
        "toTest",
        HttpMethods.GET,
        actorRef,
        r =>
          Future {
            Thread.sleep(1000)
            r
          },
        responseHandler200,
        rejectionHandler,
        responseTo,
        r => HttpResponse(StatusCodes.ImATeapot)
      )

      Get("/toTest") ~!> r ~> check {
        assert(status == StatusCodes.ImATeapot)
      }
    }
  }*/

}

object RouteGeneratorTest {

  val failMessage = "purposeful fail"

  def simpleFunction(
      in: AkkaHttpRequest
  )(implicit ec: ExecutionContext, materializer: akka.stream.Materializer): Future[RequestInfo] = {
    in.requestBody match {
      case Some(value) =>
        Unmarshaller
          .stringUnmarshaller(value)
          .map(
            requestBody =>
              RequestInfo(
                in.path,
                in.method.toString,
                in.requestHeaders,
                in.segments,
                in.queryParams,
                Some(requestBody)
              )
          )
      case None =>
        Future.successful(
          RequestInfo(in.path, in.method.toString, in.requestHeaders, in.segments, in.queryParams, None)
        )
    }
  }
  def exceptionFunction(in: AkkaHttpRequest): Future[RequestInfo] = Future.failed(new Exception(failMessage))
  def messageFunction(in: AkkaHttpRequest): Future[String] = Future.successful("Welcome to Wookiee-Akka-Http")

  def requestHandler(req: AkkaHttpRequest): Future[AkkaHttpRequest] = Future.successful(req)
  def responseHandler200(resp: RequestInfo): Route = complete(StatusCodes.OK, resp)

  def rejectionHandler(request: AkkaHttpRequest): PartialFunction[Throwable, Route] = {
    case ex: NotAuthorized => complete(StatusCodes.Unauthorized, ex.message)
    case ex: Forbidden     => complete(StatusCodes.Forbidden, ex.message)
    case t: Throwable      => complete(StatusCodes.InternalServerError, t.getMessage)
  }

  def errorOnResponse(echoed: RequestInfo): Route = {
    throw new Exception(failMessage)
  }

  def requestHandlerWithException(req: AkkaHttpRequest): Future[AkkaHttpRequest] = throw new Exception(failMessage)

  def requestHandlerWithAuthenticationFailure(req: AkkaHttpRequest): Future[AkkaHttpRequest] =
    Future.failed(NotAuthorized(failMessage))

  def requestHandlerWithUnknownFailure(req: AkkaHttpRequest): Future[AkkaHttpRequest] =
    Future.failed(new IllegalArgumentException(failMessage))

  def rejectionHandlerWithLimitedScope(request: AkkaHttpRequest): PartialFunction[Throwable, Route] = {
    case ex: NotAuthorized => complete(StatusCodes.Unauthorized, ex.message)
    case ex: Forbidden     => complete(StatusCodes.Forbidden, ex.message)
  }
}
