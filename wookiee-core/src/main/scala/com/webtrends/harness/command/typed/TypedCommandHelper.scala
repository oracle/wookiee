package com.webtrends.harness.command.typed

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait TypedCommandHelper { this: Actor =>

  var typedCommandManager: Option[ActorRef] = None
  implicit def ec: ExecutionContext = context.dispatcher

  def registerTypedCommand[T<:TypedCommand[_,_]](name: String, actorClass: Class[T], checkHealth: Boolean = false): Future[ActorRef] = {
    implicit val timeout = Timeout(2 seconds)
    getManager().flatMap { cm =>
      (cm ? RegisterCommand(name, Props(actorClass), checkHealth)).mapTo[ActorRef]
    }
  }

  protected def getManager(): Future[ActorRef] = {
    typedCommandManager match {
      case Some(cm) => Future.successful(cm)
      case None =>
        context.system.actorSelection(HarnessConstants.TypedCommandFullName).resolveOne()(2 seconds).map { s =>
          typedCommandManager = Some(s)
          s
        }
    }
  }
}
