/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.command

import akka.actor.{Props, ActorRef, Actor}
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.app.Harness
import scala.concurrent.duration._
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.logging.ActorLoggingAdapter
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

trait CommandHelper extends ActorLoggingAdapter with BaseCommandHelper {
  this: Actor =>
  override implicit val actorSystem = context.system

}
/**
 * A trait that you can add to any actor that will enable the actor to talk to the CommandManager easily
 * and execute commands at will
 *
 * @author Michael Cuthbert on 12/10/14.
 */
trait BaseCommandHelper  {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val actorSystem = Harness.getActorSystem.get

  var commandManagerInitialized = false
  var commandManager:Option[ActorRef] = None

  def initCommandHelper = {
    addCommands
  }

  def initCommandManager : Future[Boolean] = {
    val p = Promise[Boolean]
    commandManagerInitialized match {
      case true => p success commandManagerInitialized
      case false =>
        actorSystem.actorSelection(HarnessConstants.CommandFullName).resolveOne()(2 seconds) onComplete {
          case Success(s) =>
            commandManagerInitialized = true
            commandManager = Some(s)
            p success commandManagerInitialized
          case Failure(f) => p failure CommandException("Component Manager", f)
        }
    }
    p.future
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  def addCommands = {}

  /**
   * Wrapper that allows services to add commands to the command manager with a single command
   *
   * @param name name of the command you want to add
   * @param props the props for that command actor class
   * @return
   */
  def addCommandWithProps[T<:Command](name:String, props:Props, checkHealth: Boolean = false) : Future[ActorRef] = {
    implicit val timeout = Timeout(2 seconds)
    val p = Promise[ActorRef]
    initCommandManager onComplete {
      case Success(_) =>
        commandManager match {
          case Some(cm) =>
            (cm ? AddCommandWithProps(name, props, checkHealth)).mapTo[ActorRef] onComplete {
              case Success(r) => p success r
              case Failure(f) => p failure f
            }
          case None => p failure CommandException("CommandManager", "CommandManager not found!")
        }
      case Failure(f) => p failure f
    }
    p.future
  }

  /**
   * Wrapper that allows services add commands to the command manager with a single command
   *
   * @param name name of the command you want to add
   * @param actorClass the class for the actor
   */
  def addCommand[T<:Command](name:String, actorClass:Class[T], checkHealth: Boolean = false) : Future[ActorRef] = {
    implicit val timeout = Timeout(2 seconds)
    val p = Promise[ActorRef]
    initCommandManager onComplete {
      case Success(_) =>
        commandManager match {
          case Some(cm) =>
            (cm ? AddCommand(name, actorClass, checkHealth)).mapTo[ActorRef] onComplete {
              case Success(r) => p success r
              case Failure(f) => p failure f
            }
          case None => p failure CommandException("CommandManager", "CommandManager not found!")
        }
      case Failure(f) => p failure f
    }
    p.future
  }

  /**
   * Wrapper that allows services execute commands (remote or otherwise)
   *
   * @param name name of the command you want to execute
   *             if this is a remote command the name will be the reference to the
   *             command
   * @param bean the bean that will be passed to the command
   * @param server If none then we are executing a local command, if set then it is a remote command and that is the server name
   * @param port The port of the remote server defaults to 0, as by default this function deals with local commands
   * @return
   */
  def executeCommand[T:Manifest](name:String, bean:Option[CommandBean]=None, server:Option[String]=None,
                        port:Int=2552)(implicit timeout:Timeout) : Future[BaseCommandResponse[T]] = {

    val p = Promise[BaseCommandResponse[T]]
    initCommandManager onComplete {
      case Success(_) =>
        commandManager match {
          case Some(cm) =>
            val msg = server match {
              case Some(srv) => ExecuteRemoteCommand(name, srv, port, bean)
              case None => ExecuteCommand(name, bean)
            }
            (cm ? msg)(timeout).mapTo[BaseCommandResponse[T]] onComplete {
              case Success(s) => p success s
              case Failure(f) => p failure CommandException("CommandManager", f)
            }
          case None => p failure CommandException("CommandManager", "CommandManager not found!")
        }
      case Failure(f) => p failure f
    }
    p.future
  }
}
