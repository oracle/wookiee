/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * @author Michael Cuthbert & Spencer Wood
  */
sealed trait AddCommands {
  val id: String
}

case class AddCommandWithProps(id: String, props: Props, checkHealth: Boolean = false) extends AddCommands
case class AddCommand[T: ClassTag](id: String, actorClass: Class[T], checkHealth: Boolean = false) extends AddCommands
case class GetCommands()

case class ExecuteCommand[Input <: Product : ClassTag, Output <: Any : ClassTag]
  (id: String, bean: Input, timeout: Timeout)
case class ExecuteRemoteCommand[Input <: Product : ClassTag, Output <: Any : ClassTag]
  (id: String, bean: Input, remoteLogic: (String, Input) => Future[Output], timeout: Timeout)


class CommandManager extends PrepareForShutdown {
  import context.dispatcher

  // map that stores the name of the command with the actor it references
  val commandMap: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()
  val healthCheckChildren: mutable.ArrayBuffer[ActorRef] = mutable.ArrayBuffer.empty[ActorRef]

  /** Only check the health of commands that have specified that they are going to provide health information
    * @return An Iterable[ActorRef] for the commands to send health check requests to
    */
  override def getHealthChildren: Iterable[ActorRef] = {
    healthCheckChildren
  }

  override def receive: PartialFunction[Any, Unit] = super.receive orElse {
    case AddCommandWithProps(name, props, checkHealth) =>
      sender ! addCommand(name, props, checkHealth)
    case AddCommand(name, actorClass, checkHealth) =>
      sender ! addCommand(name, actorClass, checkHealth)
    case exec: ExecuteCommand[Product, Any] =>
      pipe(executeCommand(exec)) to sender
    case exec: ExecuteRemoteCommand[Product, Any] =>
      pipe(executeRemoteCommand(exec)) to sender
    case GetCommands() =>
      sender ! getCommands
    case SystemReady => // ignore
    case ex =>
      log.warn(s"Unable to handle message type: $ex")
  }

  protected def addCommand[T:ClassTag](name:String, actorClass:Class[T], checkHealth: Boolean): ActorRef = {
    addCommand(name, Props(actorClass), checkHealth)
  }

  protected def getCommand(name:String): Option[ActorRef] = commandMap.get(name)
  protected def getCommands: Map[String, ActorRef] = commandMap.toMap

  /**
   * We add commands as children to the CommandManager, based on default routing
   * or we use the setup defined for the command
   */
  protected def addCommand(name: String, props: Props, checkHealth: Boolean): ActorRef = {
    // check first if the router props have been defined else
    // use the default Round Robin approach
    getCommand(name) match {
      case Some(ref) =>
        log.warn(s"Command $name has already been registered with CommandManager.")
        ref
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
        aRef
    }
  }

  /**
   * Executes a remote command and will return a BaseCommandResponse to the sender
   * @param exec The name, server, port, and bean for the command you want to execute
   */
  protected def executeRemoteCommand[Input <: Product : ClassTag, Output <: Any : ClassTag]
  (exec: ExecuteRemoteCommand[Input, Output]): Future[Output] = {
    exec.remoteLogic(exec.id, exec.bean) recover { case f =>
      throw CommandException("CommandManager", f)
    }
  }

  /**
   * Executes a command and will return expected Output to the sender
   */
  protected def executeCommand[Input <: Product : ClassTag, Output <: Any : ClassTag]
  (exec: ExecuteCommand[Input, Output]): Future[Output] = {
    getCommand(exec.id).map { ref =>
      (ref ? exec).mapTo[Output] recover { case ex =>
        log.warn(s"Command ${exec.id} execution failed", ex)
        throw CommandException(exec.id, ex)
      }
    } getOrElse Future.failed(CommandException(exec.id, "Command not found"))
  }

  def addCommand(name:String, ref:ActorRef): commandMap.type = {
    log.info(s"Registered Command $name using path ${ref.path} with Command Manager.")
    commandMap += (name -> ref)
  }
}

object CommandManager {
  def props: Props = Props[CommandManager]
}