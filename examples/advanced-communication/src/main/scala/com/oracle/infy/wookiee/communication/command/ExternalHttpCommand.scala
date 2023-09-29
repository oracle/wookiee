package com.oracle.infy.wookiee.communication.command

import com.oracle.infy.wookiee.communication.command.ExternalHttpCommand.businessLogicToCallInternalCommand
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder, getInternalConfig}
import com.oracle.infy.wookiee.component.web.http.HttpCommand
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandExecution
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandHelper.{ZookeeperConfig, getZKConnectConfig}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.Config
import org.json4s.Formats

import scala.concurrent.{ExecutionContext, Future}

object ExternalHttpCommand extends DiscoverableCommandExecution with LoggingAdapter {

  def businessLogicToCallInternalCommand(
      implicit config: Config,
      ex: ExecutionContext,
      formats: Formats
  ): InputHolder => Future[OutputHolder] = (input: InputHolder) => {
    log.info(s"Executing business logic for input [${input.input}]")
    executeDiscoverableCommand[InputHolder, OutputHolder](
      ZookeeperConfig(
        getInternalConfig(config, "zk-path"),
        getZKConnectConfig(config).getOrElse("localhost:3181"),
        getInternalConfig(config, "bearer-token"),
        None
      ),
      InternalDiscoverableCommand.commandName,
      input.copy(input = "Input: " + input.input)
    )
  }
}

// The implicit arguments are required for the executeDiscoverableCommand method
class ExternalHttpCommand(implicit config: Config, formats: Formats, ec: ExecutionContext) extends HttpCommand {

  override def commandName: String = "object-oriented-endpoint"

  override def method: String = "GET"

  // Corresponds to the path /external/* where '*' can be anything and will be passed along to the execution
  override def path: String = "/external/$somevalue"

  /**
    * Will this endpoint be exposed to external clients, internal clients, or both?
    */
  override def endpointType: EndpointType = EndpointType.BOTH

  override def endpointOptions: EndpointOptions =
    EndpointOptions
      .default
      .copy(defaultHeaders = Headers(Map("Default-Wookiee-Header" -> List("Default Header Value"))))

  // Same exact functionality as the functional method of registration in ExternalWookieeService
  override def execute(input: WookieeRequest): Future[WookieeResponse] = {
    log.info(s"Got a request with content=[${input.content.asString}]")
    val inputHolder = InputHolder(input.pathSegments("somevalue"))
    val commandLogic = businessLogicToCallInternalCommand
    commandLogic(inputHolder)
      .map { output =>
        WookieeResponse(
          Content(output.output)
        )
      }
      .recover {
        case err: Throwable =>
          WookieeResponse(
            Content(err.getMessage),
            StatusCode(500)
          )
      }

  }
}
