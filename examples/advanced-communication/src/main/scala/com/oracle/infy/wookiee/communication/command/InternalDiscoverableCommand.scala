package com.oracle.infy.wookiee.communication.command

import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder}
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommand
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object InternalDiscoverableCommand {
  val commandName = "internalDiscoverableCommand"
  case class InputHolder(input: String)
  case class OutputHolder(output: String)

  def getInternalConfig(config: Config, prop: String): String = config.getString(s"example-config.internal.$prop")
}

class InternalDiscoverableCommand extends DiscoverableCommand[InputHolder, OutputHolder] {
  // Must be unique for each command
  override def commandName: String = InternalDiscoverableCommand.commandName

  // Main business logic
  override def execute(args: InputHolder): Future[OutputHolder] = Future {
    log.info(s"Executing command: $commandName, [${args.input}]")
    OutputHolder(args.input + ", Output: Hello World!")
  }
}
