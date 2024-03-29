package com.oracle.infy.wookiee.command

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.command.WookieeCommandExecutive.ExecuteCommand
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}

import scala.concurrent.Future
import scala.reflect.runtime.universe._

/**
  * A WookieeCommand is a command that can be executed by the WookieeCommandManager
  * and is a WookieeHealth component. The commandName is the name of the command
  * and the execute method is the primary entry point for the command.
  */
abstract class WookieeCommand[Input <: Any: TypeTag, +Output <: Any: TypeTag] extends WookieeActor {
  def commandName: String = name

  /**
    * The primary entry point for the command, the actor for this command
    * will ignore all other messaging and only execute through this
    */
  def execute(args: Input): Future[Output]

  // Override for custom health check logic
  override def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(commandName, ComponentState.NORMAL, s"Command [$commandName] is healthy."))

  override protected def receive: Receive = super.receive orElse {
    case ExecuteCommand(input) =>
      pipe(execute(input.asInstanceOf[Input]))
  }
}
