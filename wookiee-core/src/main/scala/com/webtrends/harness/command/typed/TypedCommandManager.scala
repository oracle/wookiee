package com.webtrends.harness.command.typed

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.{FromConfig, RoundRobinPool}
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.health.{ActorHealth, ComponentState, HealthComponent}
import com.webtrends.harness.logging.LoggingAdapter

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

case class RegisterCommand[T<:TypedCommand[_,_]](name:String, props: Props, checkHealth: Boolean)

class TypedCommandManager extends Actor with ActorHealth with LoggingAdapter {

  val healthCheckChildren = mutable.ArrayBuffer.empty[ActorRef]

  val config = context.system.settings.config.getConfig("akka.actor.deployment")

  override def receive: Receive = health orElse {
    case RegisterCommand(name, props, checkHealth) => sender ! registerCommand(name, props, checkHealth)
  }

  def registerCommand[T<:TypedCommand[_,_]](name: String, actorProps: Props, checkHealth: Boolean): ActorRef = {
    TypedCommandManager.commands.get(name) match {
      case Some(commandRef) =>
        log.warn(s"Command $name has already been added, not re-adding it.")
        commandRef
      case None =>
        val props = if (config.hasPath(s"akka.actor.deployment.${HarnessConstants.TypedCommandFullName}/$name")) {
          FromConfig.props(actorProps)
        } else {
          val nrRoutees = Try { config.getInt(HarnessConstants.KeyCommandsNrRoutees) }.getOrElse(5)
          RoundRobinPool(nrRoutees).props(actorProps)
        }
        val commandRef = context.actorOf(props, name)
        TypedCommandManager.commands(name) = commandRef
        if (checkHealth) {
          healthCheckChildren += commandRef
        }
        commandRef
    }
  }

  override def getHealthChildren: Iterable[ActorRef] = {
    healthCheckChildren
  }

  override def getHealth: Future[HealthComponent] = {
    Future.successful(
      HealthComponent(self.path.toString, ComponentState.NORMAL,
        s"Managing ${TypedCommandManager.commands.size} typed commands")
    )
  }
}

object TypedCommandManager {
  private[typed] val commands = mutable.Map[String, ActorRef]()
  def props = Props[TypedCommandManager]
}