package com.webtrends.harness.component.zookeeper.discoverable

import java.util.UUID

import akka.actor.{ActorRef, Props, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import com.webtrends.harness.command._

import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

/**
 * @author Michael Cuthbert on 7/10/15.
 */
trait DiscoverableCommandHelper extends CommandHelper with Discoverable {
  this: Actor =>
  import context.dispatcher
  implicit val basePath:String

  /**
   * Wrapper that allows services to add commands to the command manager with a single discoverable command
   *
   * @param name name of the command you want to add
   * @param props the props for that command actor class
   * @return
   */
  def addDiscoverableCommandWithProps[T<:Command](name:String, props:Props, id:Option[String]=None) : Future[ActorRef] = {
    implicit val timeout = Timeout(2 seconds)
    val idValue = id match {
      case Some(i) => i
      case None => UUID.randomUUID().toString
    }
    val p = Promise[ActorRef]
    initCommandManager onComplete {
      case Success(_) =>
        commandManager match {
          case Some(cm) =>
            (cm ? AddCommandWithProps(name, props)).mapTo[ActorRef] onComplete {
              case Success(r) =>
                makeDiscoverable(basePath, idValue, name)
                p success r
              case Failure(f) => p failure f
            }
          case None => p failure CommandException("CommandManager", "CommandManager not found!")
        }
      case Failure(f) => p failure f
    }
    p.future
  }

  /**
   * Wrapper that allows services add commands to the command manager with a single discoverable command
   *
   * @param name name of the command you want to add
   * @param actorClass the class for the actor
   */
  def addDiscoverableCommand[T<:Command](name:String, actorClass:Class[T], id:Option[String]=None) : Future[ActorRef] = {
    implicit val timeout = Timeout(2 seconds)
    val idValue = id match {
      case Some(i) => i
      case None => UUID.randomUUID().toString
    }
    val p = Promise[ActorRef]
    initCommandManager onComplete {
      case Success(_) =>
        commandManager match {
          case Some(cm) =>
            (cm ? AddCommand(name, actorClass)).mapTo[ActorRef] onComplete {
              case Success(r) =>
                makeDiscoverable(basePath, idValue, name)
                p success r
              case Failure(f) => p failure f
            }
          case None => p failure CommandException("CommandManager", "CommandManager not found!")
        }
      case Failure(f) => p failure f
    }
    p.future
  }
}
