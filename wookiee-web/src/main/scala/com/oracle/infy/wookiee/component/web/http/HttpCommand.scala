package com.oracle.infy.wookiee.component.web.http

import com.oracle.infy.wookiee.command.WookieeCommand
import com.oracle.infy.wookiee.component.web.WebManager.WookieeWebException
import com.oracle.infy.wookiee.component.web.http.HttpObjects._

import java.util.concurrent.TimeoutException
import scala.concurrent.Future
import scala.util.Try

/**
  * Trait to extend for each HTTP endpoint you want to expose, as an alternative to
  * the functional method at HelidonManager.registerEndpoint. After implementing the
  * required methods, you can register the endpoint by calling:
  *   HelidonManager.registerEndpoint(command)
  */
trait HttpCommand extends WookieeCommand[WookieeRequest, WookieeResponse] {
  // Can be one of: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE
  def method: String

  /**
    *  The path to register this endpoint at, should have a leading slash, and can
    *  contain path parameters (prefixed with '$') that will be accessible later.
    *  For example: "my/path/$param1/$param2"
    */
  def path: String

  /**
    *  Will this endpoint be exposed to external clients, internal clients, or both?
    *  This setting will effect which ports this endpoints shows up on as defined in
    *  the config under path "wookiee-web.(internal|external)-port".
    */
  def endpointType: EndpointType.EndpointType

  // Variety of options like default headers, CORS, and metrics labels
  def endpointOptions: EndpointOptions = EndpointOptions.default

  // Any uncaught errors from the execute method or anywhere else in processing will
  // be passed to this handler to be converted into a response.
  // If a WookieeWebException is thrown we'll use its information to create the response.
  def errorHandler(request: WookieeRequest, ex: Throwable): WookieeResponse = {
    Try(log.debug(s"WHH404: Detailed error in HTTP handling of path [$path], method [$method], request [$request]", ex))
    ex match {
      case WookieeWebException(msg, _, code) =>
        log.warn(s"WHH400: Error in HTTP handling of path [$path], method [$method]", ex)
        WookieeResponse(
          Content(msg),
          HttpObjects.StatusCode(code.getOrElse(500)),
          endpointOptions.defaultHeaders
        )
      case _: TimeoutException =>
        log.warn(s"WHH401: Timeout in HTTP handling of path [$path], method [$method]")
        WookieeResponse(
          Content("Request timed out."),
          HttpObjects.StatusCode(504),
          endpointOptions.defaultHeaders
        )
      case _ =>
        log.warn(s"WHH402: Internal Error in HTTP handling of path [$path], method [$method]", ex)
        WookieeResponse(
          Content("There was an internal server error."),
          HttpObjects.StatusCode(500),
          endpointOptions.defaultHeaders
        )
    }
  }

  // Any logic that needs to happen before the request is passed to the execute method
  def requestDirective(request: WookieeRequest): Future[WookieeRequest] =
    Future.successful(request)

  // The main method to implement your business logic and response
  override def execute(input: WookieeRequest): Future[WookieeResponse]
}
