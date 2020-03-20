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

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * @author Michael Cuthbert & Spencer Wood
  */
case class AddCommandWithProps(name:String, props:Props, checkHealth: Boolean = false)
case class AddCommand[T:ClassTag](name:String, actorClass:Class[T], checkHealth: Boolean = false)
case class ExecuteCommand[Input<: Product : ClassTag](name:String, bean:Input, timeout: Timeout)
case class ExecuteRemoteCommand[Input<: Product : ClassTag](name:String, server:String, port:Int, bean:Input, timeout: Timeout)
case class GetCommands()

class CommandManager extends PrepareForShutdown {

  import context.dispatcher

  // map that stores the name of the command with the actor it references
  val commandMap: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()
  val healthCheckChildren: mutable.ArrayBuffer[ActorRef] = mutable.ArrayBuffer.empty[ActorRef]

  /** Only check the health of commands that have specified that they are going to provide health information
    *
    * @return An Iterable[ActorRef] for the commands to send health check requests to
    */
  override def getHealthChildren: Iterable[ActorRef] = {
    healthCheckChildren
  }

  override def receive: PartialFunction[Any, Unit] = super.receive orElse {
    case AddCommandWithProps(name, props, checkHealth) => pipe(addCommand(name, props, checkHealth)) to sender
    case AddCommand(name, actorClass, checkHealth) => pipe(addCommand(name, actorClass, checkHealth)) to sender
    case ExecuteCommand(name, bean, timeout) => pipe(executeCommand(name, bean, timeout)) to sender
    case ExecuteRemoteCommand(name, server, port, bean, timeout) => pipe(executeRemoteCommand(name, server, port, bean, timeout)) to sender
    case GetCommands() => sender ! getCommands
    case SystemReady => // ignore
  }

  protected def addCommand[T:ClassTag](name:String, actorClass:Class[T], checkHealth: Boolean) : Future[ActorRef] = {
    addCommand(name, Props(actorClass), checkHealth)
  }

  protected def addCommand(name:String, ref:ActorRef): commandMap.type = {
    log.debug(s"Command $name with path ${ref.path} inserted into Command Manager map.")
    commandMap += (name -> ref)
  }

  protected def getCommand(name:String) : Option[ActorRef] = commandMap.get(name)
  protected def getCommands: Map[String, ActorRef] = commandMap.toMap
  protected def getRemoteAkkaPath(server:String, port:Int) : String = s"akka.tcp://server@$server:$port${HarnessConstants.CommandFullName}"

  /**
   * We add commands as children to the CommandManager, based on default routing
   * or we use the setup defined for the command
   */
  protected def addCommand(name:String, props:Props, checkHealth: Boolean) : Future[ActorRef] = {
    // check first if the router props have been defined else
    // use the default Round Robin approach
    getCommand(name) match {
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

        addCommand(name, aRef)
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
  protected def executeRemoteCommand[Input <:Product : ClassTag, Output: ClassTag](name:String, server:String,
                                        port:Int=2552, bean:Input, timeout: Timeout) : Future[Output] = {
    val p = Promise[Output]
    config.getString("akka.actor.provider") match {
      case "akka.remote.RemoteActorRefProvider" =>
        context.actorSelection(getRemoteAkkaPath(server, port)).resolveOne() onComplete {
          case Success(ref) =>
            execCommand(ref, ExecuteCommand(name, bean, timeout), timeout, p)
          case Failure(f) => p failure new CommandException("CommandManager", s"Failed to find remote system [$server:$port]", Some(f))
        }
      case _ => p failure new CommandException("CommandManager", s"Remote provider for akka is not enabled")
    }
    p.future
  }

  /**
   * Executes a command and will return a BaseCommandResponse to the sender
   */
  protected def executeCommand[Input <:Product : ClassTag, Output: ClassTag](name:String, bean:Input, timeout: Timeout) : Future[Output] = {
    val p = Promise[Output]()
    getCommand(name) match {
      case Some(ref) =>
        execCommand(ref, ExecuteCommand(name, bean, timeout), timeout, p)
      case None =>
        p failure CommandException(name, "Command not found")
    }
    p.future
  }

  private def execCommand[Input <:Product : ClassTag, Output: ClassTag]
  (ref: ActorRef, exec: ExecuteCommand[Input], timeout: Timeout, promise: Promise[Output]): Unit = {
    (ref ? exec)(timeout).mapTo[Output] onComplete {
      case Success(s) => promise success s
      case Failure(f) => promise failure f
    }
  }
}

object CommandManager {
  def props: Props = Props[CommandManager]
}