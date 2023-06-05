package com.oracle.infy.wookiee.communication.command

import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder}
import com.oracle.infy.wookiee.component.discovery.command.DiscoverableCommand
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
  override def commandName: String = InternalDiscoverableCommand.commandName

  override def execute(args: InputHolder): Future[OutputHolder] = Future {
    OutputHolder(args.input + ", Output: Hello World!")
  }
}
