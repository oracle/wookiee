package com.oracle.infy.wookiee.command

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent, WookieeHealth}
import com.typesafe.config.Config

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

// Since this extends Mediator, can be accessed to retrieve current instance of WookieeCommandManager
object WookieeCommandExecutive extends Mediator[WookieeCommandExecutive]

class WookieeCommandExecutive(override val name: String, config: Config)(implicit ec: ExecutionContext)
    extends WookieeHealth {
  import WookieeCommandExecutive._
  registerMediator(getInstanceId(config), this)

  protected[command] val commands = new ConcurrentHashMap[String, WookieeCommand[Any, Any]]()

  // Call this to execute a previously registered command, which returns a Future[CommandOutput].
  // Be sure to specify the type of CommandOutput, otherwise it will be Any.
  def executeCommand[O <: Any](name: String, input: Any): Future[O] = {
    getCommand(name) match {
      case Some(command) =>
        Future {
          command.execute(input).map(_.asInstanceOf[O]) // scalafix:ok
        }.flatten
      case None =>
        throw new IllegalArgumentException(s"Command $name not found.")
    }
  }

  /**
    * Registers a command with the manager, if the command has already been registered
    * it will not be re-registered.
    * @param command The command to register which has an execute() method
    */
  def registerCommand(command: WookieeCommand[_ <: Any, _ <: Any]): Unit = {
    getCommand(command.commandName) match {
      case Some(_) =>
        log.warn(s"Command [${command.commandName}] has already been added, not re-adding it.")
      case None =>
        log.info(s"Registering command: [${command.commandName}]")
        commands.put(command.commandName, command.asInstanceOf[WookieeCommand[Any, Any]])
        ()
    }
  }

  // Call this to retrieve a previously registered command
  def getCommand(name: String): Option[WookieeCommand[Any, Any]] = Option(commands.get(name))

  override def getDependentHealths: Iterable[WookieeHealth] =
    commands.values().asScala.toList

  override def getHealth: Future[HealthComponent] = {
    Future.successful(
      HealthComponent(
        name,
        ComponentState.NORMAL,
        s"Managing [${commands.size}] Wookiee commands"
      )
    )
  }
}
