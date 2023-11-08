package com.oracle.infy.wookiee.command

import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.Config

import scala.concurrent.Future

// Helper method and trait to make it easy to register and execute commands
object WookieeCommandHelper {

  // This method will register a command with the WookieeCommandManager
  // The command will be registered with the commandName as the name of the command
  // The config must contain 'instance-id' which it will by default in a Wookiee Service
  // If nrRoutees is None, we'll use the config at 'commands.default-nr-routees'
  def registerCommand(command: => WookieeCommand[_, _], nrRoutees: Option[Int] = None)(implicit config: Config): Unit =
    nrRoutees match {
      case Some(routees) =>
        WookieeCommandExecutive.getMediator(config).registerCommand(command, routees)
      case None =>
        WookieeCommandExecutive.getMediator(config).registerCommand(command)
    }

  // This method will execute a command with the given name and input
  // It must have been previously registered using the registerCommand method
  def executeCommand[Output <: Any](name: String, input: Any)(implicit config: Config): Future[Output] =
    WookieeCommandExecutive.getMediator(config).executeCommand(name, input)
}

trait WookieeCommandHelper extends LoggingAdapter {
  val config: Config

  def registerCommand(command: WookieeCommand[_, _]): Unit =
    WookieeCommandHelper.registerCommand(command)(config)

  def executeCommand[Output <: Any](name: String, input: Any): Future[Output] =
    WookieeCommandHelper.executeCommand(name, input)(config)
}
