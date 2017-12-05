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

import akka.actor.{ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.routing.{FromConfig, RoundRobinPool}
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.HarnessActor.SystemReady
import com.webtrends.harness.app.PrepareForShutdown
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * @author Michael Cuthbert & Spencer Wood
  */
case class AddCommandWithProps[T<:Command](name:String, props:Props, checkHealth: Boolean = false)
case class AddCommand[T<:Command](name:String, actorClass:Class[T], checkHealth: Boolean = false)
case class ExecuteCommand[T:Manifest](name:String, bean:Option[CommandBean]=None, timeout: Timeout)
case class ExecuteRemoteCommand[T:Manifest](name:String, server:String, port:Int, bean:Option[CommandBean]=None, timeout: Timeout)

@SerialVersionUID(100L)
case class CommandResponse[T:Manifest](data:Option[T], responseType:String="json") extends BaseCommandResponse[T]

trait BaseCommandResponse[T] {
  val data:Option[T]
  val responseType: String
}

class CommandManager extends PrepareForShutdown {

  import context.dispatcher

  val healthCheckChildren = mutable.ArrayBuffer.empty[ActorRef]

  /** Only check the health of commands that have specified that they are going to provide health information
    *
    * @return An Iterable[ActorRef] for the commands to send health check requests to
    */
  override def getHealthChildren: Iterable[ActorRef] = {
    healthCheckChildren
  }

  override def receive = super.receive orElse {
    case AddCommandWithProps(name, props, checkHealth) => pipe(addCommand(name, props, checkHealth)) to sender
    case AddCommand(name, actorClass, checkHealth) => pipe(addCommand(name, actorClass, checkHealth)) to sender
    case ExecuteCommand(name, bean, timeout) => pipe(executeCommand(name, bean, timeout)) to sender
    case ExecuteRemoteCommand(name, server, port, bean, timeout) => pipe(executeRemoteCommand(name, server, port, bean, timeout)) to sender
    case SystemReady => // ignore
  }

  /**
   * Wrapper around the addCommand function that creates a function with props, this will just get the
   * props from the actor class
   *
   * @param name name of the command you want to add
   * @param actorClass the actor class for the command
   */
  protected def addCommand[T<:Command](name:String, actorClass:Class[T], checkHealth: Boolean) : Future[ActorRef] = {
    addCommand[T](name, Props(actorClass), checkHealth)
  }

  /**
   * We add commands as children to the CommandManager, based on default routing
   * or we use the setup defined for the command
   */
  protected def addCommand[T<:Command](name:String, props:Props, checkHealth: Boolean) : Future[ActorRef] = {
    // check first if the router props have been defined else
    // use the default Round Robin approach
    CommandManager.getCommand(name) match {
      case Some(ref) =>
        log.warn(s"Command $name has already been added, not re-adding it.")
        Future.successful(ref)
      case None =>
        val aRef = if (!config.hasPath(s"akka.actor.deployment.${HarnessConstants.CommandFullName}/$name")) {
          val nrRoutees = config.getInt(HarnessConstants.KeyCommandsNrRoutees)
          context.actorOf(RoundRobinPool(nrRoutees).props(props), name)
        } else {
          context.actorOf(FromConfig.props(props), name)
        }

        if (checkHealth) {
          healthCheckChildren += aRef
        }

        CommandManager.addCommand(name, aRef)
        Future { aRef }
    }
  }

  /**
   * Executes a remote command and will return a BaseCommandResponse to the sender
   *
   * @param name The name of the command you want to execute
   * @param server The server that has the command on
   * @param port the port that the server is listening on
   * @param bean Map of parameters
   */
  protected def executeRemoteCommand[T:Manifest](name:String, server:String,
                                        port:Int=2552, bean:Option[CommandBean]=None, timeout: Timeout) : Future[BaseCommandResponse[T]] = {
    val p = Promise[BaseCommandResponse[T]]
    config.getString("akka.actor.provider") match {
      case "akka.remote.RemoteActorRefProvider" =>
        context.actorSelection(CommandManager.getRemoteAkkaPath(server, port)).resolveOne() onComplete {
          case Success(ref) =>
            (ref ? ExecuteCommand(name, bean, timeout))(timeout).mapTo[BaseCommandResponse[T]] onComplete {
              case Success(s) => p success s
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure new CommandException("CommandManager", s"Failed to find remote system [$server:$port]", Some(f))
        }
      case _ => p failure new CommandException("CommandManager", s"Remote provider for akka is not enabled")
    }
    p.future
  }

  /**
   * Executes a command and will return a BaseCommandResponse to the sender
   */
  protected def executeCommand[T:Manifest](name:String, bean:Option[CommandBean]=None, timeout: Timeout) : Future[BaseCommandResponse[T]] = {
    val p = Promise[BaseCommandResponse[T]]
    CommandManager.getCommand(name) match {
      case Some(ref) =>
        (ref ? ExecuteCommand(name, bean, timeout))(timeout).mapTo[BaseCommandResponse[T]] onComplete {
          case Success(s) => p success s
          case Failure(f) => p failure f
        }
      case None => p failure CommandException(name, "Command not found")
    }
    p.future
  }
}

object CommandManager {
  private val externalLogger = LoggerFactory.getLogger(this.getClass)

  // map that stores the name of the command with the actor it references
  val commandMap = mutable.Map[String, ActorRef]()

  def props = Props[CommandManager]

  def addCommand(name:String, ref:ActorRef) = {
    externalLogger.debug(s"Command $name with path ${ref.path} inserted into Command Manager map.")
    commandMap += (name -> ref)
  }

  def getCommand(name:String) : Option[ActorRef] = commandMap.get(name)

  def getCommands() : Option[Map[String, ActorRef]] = Some(commandMap.toMap)

  def getRemoteAkkaPath(server:String, port:Int) : String = s"akka.tcp://server@$server:$port${HarnessConstants.CommandFullName}"
}