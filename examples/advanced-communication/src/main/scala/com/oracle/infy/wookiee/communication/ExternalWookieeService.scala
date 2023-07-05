package com.oracle.infy.wookiee.communication

import com.oracle.infy.wookiee.communication.command.ExternalHttpCommand
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder}
import com.oracle.infy.wookiee.communication.ws.{AuthHolder, ExternalWSHandler}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.ws.WebsocketInterface
import com.oracle.infy.wookiee.component.helidon.web.{WookieeEndpoints, WookieeHttpService}
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandExecution
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.{ExecutionContext, Future}

class ExternalWookieeService(config: Config) extends WookieeHttpService(config) with DiscoverableCommandExecution {
  implicit val ec: ExecutionContext = ThreadUtil.createEC("external-wookiee-service")

  override val name: String = "External Wookiee Service"

  override def addCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    log.info("Adding external commands")
    implicit val formats: Formats = DefaultFormats

    // Functional method of HTTP registration hosted on /external/$anything/functional
    // Exact same functionality as the object oriented method below
    // Will grab the segment from the path and pass it along as the main content
    WookieeEndpoints.registerEndpoint[InputHolder, OutputHolder](
      name = "functional-endpoint",
      path = "/external/$anything/functional",
      method = "GET",
      endpointType = EndpointType.EXTERNAL,
      requestHandler = { req =>
        log.info(s"Got a request with content=[${req.content.asString}]")
        Future.successful(InputHolder(req.pathSegments("anything")))
      },
      businessLogic = ExternalHttpCommand.businessLogicToCallInternalCommand,
      responseHandler = (output: OutputHolder) => WookieeResponse(Content(output.output)),
      errorHandler = (err: Throwable) => WookieeResponse(Content(err.getMessage), StatusCode(500)),
      endpointOptions = EndpointOptions
        .default
        .copy(defaultHeaders = Headers(Map("Default-Wookiee-Header" -> List("Default Header Value"))))
    )

    // Object Oriented method of HTTP registration
    WookieeEndpoints.registerEndpoint(new ExternalHttpCommand)

    // Functional method of WS registration
    // Exact same functionality (except a few error cases) as the object oriented method below
    WookieeEndpoints.registerWebsocket(
      path = "/ws/$userId/functional",
      endpointType = EndpointType.BOTH,
      handleInMessage = (text: String, interface: WebsocketInterface, authInfo: Option[AuthHolder]) => {
        log.info(s"Received message [$text] from user [${authInfo.map(_.userId).getOrElse("no-auth")}]")
        interface.reply(
          s"Received message [$text] from user [${authInfo.map(_.userId).getOrElse("no-auth")}], " +
            s"with auth token [${authInfo.map(_.token).getOrElse("no-auth")}]"
        )
      },
      authHandler = (request: WookieeRequest) =>
        Future {
          val userId = request.pathSegments("userId")
          Some(
            request
              .queryParameters
              .get("token")
              .map(AuthHolder(_, userId))
              .getOrElse(AuthHolder("no-auth", userId))
          )
        },
      onCloseHandler = (auth: Option[AuthHolder]) =>
        log.info(s"Closing websocket session from user [${auth.map(_.userId).getOrElse("no-auth")}]"),
      wsErrorHandler = (interface: WebsocketInterface, _: Option[AuthHolder]) => {
        case _: IllegalArgumentException =>
          interface.stop(Some(("You must be authenticated to use this websocket", 1008)))
        case _: Throwable =>
          log.error(s"Unexpected error handling websocket message, skipping message")
          interface.reply("Unexpected error handling websocket message, skipping message")
      },
      endpointOptions = EndpointOptions
        .default
        .copy(defaultHeaders = Headers(Map("Default-Wookiee-Header" -> List("Default Header Value"))))
    )

    // Object Oriented method of WS registration
    // Exactly the same functionality as the functional method above (with some extra error demonstrations)
    WookieeEndpoints.registerWebsocket[AuthHolder](new ExternalWSHandler)
  }
}
