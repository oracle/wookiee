package com.oracle.infy.wookiee.command

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.router.RoundRobinRouter
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent, WookieeMonitor}
import com.typesafe.config.Config

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

// Since this extends Mediator, can be accessed to retrieve current instance of WookieeCommandManager
object WookieeCommandExecutive extends Mediator[WookieeCommandExecutive] {
  val KeyCommandsNrRoutees = "commands.default-nr-routees"
  case class ExecuteCommand(input: Any)

  // Call this to execute a previously registered command, which returns a Future[CommandOutput]
  // Be sure to specify the type of CommandOutput, otherwise it will be Any
  def executeCommand[O <: Any: ClassTag](name: String, input: Any)(implicit config: Config): Future[O] =
    getMediator(config).executeCommand[O](name, input)

  /**
    * Registers a command with the manager, if the command has already been registered
    * it will not be re-registered
    * Pass in an instantiator for the command, which we will use to create as many instances
    * of the command as specified in the config at 'wookiee-command.command-executive.command-instances'
    * @param command The command to register which has an execute() method
    */
  def registerCommand(command: => WookieeCommand[_ <: Any, _ <: Any])(implicit config: Config): Unit =
    getMediator(config).registerCommand(command)
}

// This is the class that will be used to store and execute V2 commands
class WookieeCommandExecutive(override val name: String, config: Config) extends WookieeMonitor {
  import WookieeCommandExecutive._
  registerMediator(getInstanceId(config), this)

  protected[command] val commands = new ConcurrentHashMap[String, WookieeActor]()

  // Call this to execute a previously registered command, which returns a Future[CommandOutput]
  // Be sure to specify the type of CommandOutput, otherwise it will be Any
  def executeCommand[O <: Any: ClassTag](name: String, input: Any): Future[O] = {
    getCommand(name) match {
      case Some(command) =>
        (command ? ExecuteCommand(input)).mapTo[O]
      case None =>
        throw new IllegalArgumentException(s"Command $name not found.")
    }
  }

  /**
    * Registers a command with the manager, if the command has already been registered
    * it will not be re-registered
    * @param command The command to register which has an execute() method
    */
  def registerCommand(command: => WookieeCommand[_ <: Any, _ <: Any]): Unit = {
    getCommand(command.commandName) match {
      case Some(_) =>
        log.warn(s"Command [${command.commandName}] has already been added, not re-adding it.")
      case None =>
        log.info(s"Registering command: [${command.commandName}]")
        val nrRoutees = Try(config.getInt(KeyCommandsNrRoutees)).getOrElse(1)
        commands.put(command.commandName, WookieeActor.withRouter(command, new RoundRobinRouter(nrRoutees)))
        ()
    }
  }

  // Call this to retrieve a previously registered command
  protected[oracle] def getCommand(name: String): Option[WookieeActor] = Option(commands.get(name))

  override def getDependents: Iterable[WookieeMonitor] =
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
