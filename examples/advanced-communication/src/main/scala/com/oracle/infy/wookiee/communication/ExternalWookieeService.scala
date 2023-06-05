package com.oracle.infy.wookiee.communication

import com.oracle.infy.wookiee.communication.command.ExternalHttpCommand
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder}
import com.oracle.infy.wookiee.component.discovery.command.DiscoverableCommandExecution
import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.web.WookieeHttpService
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.typesafe.config.Config
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.{ExecutionContext, Future}

class ExternalWookieeService(config: Config) extends WookieeHttpService(config) with DiscoverableCommandExecution {
  override val name: String = "External Wookiee Service"

  override def addCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    log.info("Adding external commands")
    implicit val formats: Formats = DefaultFormats

    // Functional method of registration hosted on /external/$anything/functional
    // Exact same functionality as the object oriented method below
    // Will grab the segment from the path and pass it along as the main content
    HelidonManager.registerEndpoint[InputHolder, OutputHolder](
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

    // Object Oriented method of registration
    HelidonManager.registerEndpoint(new ExternalHttpCommand)
  }
}
