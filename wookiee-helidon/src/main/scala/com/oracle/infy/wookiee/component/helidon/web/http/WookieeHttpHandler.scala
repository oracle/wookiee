package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.command.WookieeCommand
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._

import scala.concurrent.Future

/**
  * Trait to extend for each HTTP endpoint you want to expose, as an alternative to
  * the functional method at HelidonManager.registerEndpoint. After implementing the
  * required methods, you can register the endpoint by calling:
  *   HelidonManager.registerEndpoint(command)
  */
trait WookieeHttpHandler extends WookieeCommand[WookieeRequest, WookieeResponse] {
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
    *  the config under path "wookiee-helidon.web.(internal|external)-port".
    */
  def endpointType: EndpointType.EndpointType

  // Variety of options like default headers, CORS, and metrics labels
  def endpointOptions: EndpointOptions = EndpointOptions.default

  // Any uncaught errors from the execute method or anywhere else in processing will
  // be passed to this handler to be converted into a response.
  def errorHandler(ex: Throwable): WookieeResponse = {
    log.warn(s"WHH400: Error in HTTP handling of path [$path], method [$method]", ex)
    WookieeResponse(
      Content("There was an internal server error."),
      HttpObjects.StatusCode(500),
      endpointOptions.defaultHeaders
    )
  }

  // The main method to implement your business logic and response
  override def execute(input: WookieeRequest): Future[WookieeResponse]
}
