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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * A trait that you can add to any actor that will enable the actor to talk to the CommandManager easily
 * and execute commands at will
 */
object CommandHelper {
  def getCommandManager(implicit system: ActorSystem, timeout: Timeout = 2.seconds): Future[ActorRef] = {
    system.actorSelection(HarnessConstants.CommandFullName).resolveOne()(timeout)
  }

  /**
    * Wrapper that allows services execute commands (remote or otherwise)
    *
    * @param id name of the command you want to execute
    *             if this is a remote command the name will be the reference to the
    *             command
    * @param bean the bean that will be passed to the command
    * @param server If none then we are executing a local command, if set then it is a remote command and that is the server name
    * @param port The port of the remote server defaults to 0, as by default this function deals with local commands
    * @return Result of executing this Command
    */
  def executeCommand[Input <: Product : ClassTag, Output <: Any : ClassTag](id: String,
                                                                           bean: Input,
                                                                           server:Option[String] = None,
                                                                           port: Int = 2552,
                                                                           cm: Option[ActorRef] = None)
                                                                          (implicit system: ActorSystem, timeout: Timeout): Future[Output] = {
    import system.dispatcher

    cm.map(Future.successful).getOrElse(getCommandManager) flatMap { cm: ActorRef =>
      val msg = server match {
        case Some(srv) => ExecuteRemoteCommand(id, srv, port, bean, timeout)
        case None => ExecuteCommand(id, bean, timeout)
      }

      (cm ? msg)(timeout).mapTo[Output]
    }
  }
}

trait CommandHelper  { this: Actor =>
  import context.dispatcher

  lazy implicit val actorSystem: ActorSystem = context.system

  var commandManager: Option[ActorRef] = None

  def initCommandHelper(): Unit = {
    addCommands()
  }

  def initCommandManager : Future[ActorRef] = {
    commandManager match {
      case Some(cm) =>
        Future.successful(cm)
      case None =>
        CommandHelper.getCommandManager(actorSystem) map { cm =>
          commandManager = Some(cm)
          cm
        } recover { case f: Throwable =>
          throw CommandException("Component Manager", f)
        }
    }
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  def addCommands(): Unit = {}

  /**
    * Will add a new Command with the given `businessLogic` to this instance of Wookiee, you can call
    * it later with the same `id` using
    * @param id id of the Command you want to add, should be unique as it's used to call the Command after
    * @param businessLogic Function that takes in and returns any types, the main Command functionality
    * @tparam U Input type
    * @tparam V Output type
    * @return Reference to the newly created Command Actor
    */
  def addCommand[U <: Product: ClassTag, V: ClassTag](id: String,
                                                      businessLogic: U => Future[V]): Future[ActorRef] = {
    val props = CommandFactory.createCommand[U, V](businessLogic)
    addCommandWithProps(id, props)
  }

  /**
    * Will add a new Command with the given `businessLogic` to this instance of Wookiee, you can call
    * it later with the same `id` using
    * @param id id of the Command you want to add, should be unique as it's used to call the Command after
    * @param businessLogic Function that takes in and returns any types, the main Command functionality
    * @param customUnmarshaller Takes a Bean and converts it to the desired `U` type
    * @param customMarshaller Takes a result type `V` and converts it to the final, returned byte array
    * @tparam U Input type
    * @tparam V Output type
    * @return Reference to the newly created Command Actor
    */
  def addCommand[U <: Product: ClassTag, V: ClassTag](id: String,
                                                      customUnmarshaller: Bean => U,
                                                      businessLogic: U => Future[V],
                                                      customMarshaller: V => Array[Byte]): Future[ActorRef] = {
    val props = CommandFactory.createCommand[U, V](customUnmarshaller, businessLogic, customMarshaller)
    addCommandWithProps(id, props)
  }

  /**
   * Wrapper that allows services to add commands to the command manager with a single command
   * @param id id of the Command you want to add, should be unique as it's used to call the Command after
   * @param props the props for that command actor class
   * @return Reference to the newly created Command Actor
   */
  def addCommandWithProps(id: String, props: Props, checkHealth: Boolean = false) : Future[ActorRef] = {
    implicit val timeout: Timeout = Timeout(4.seconds)

    initCommandManager flatMap { cm =>
      (cm ? AddCommandWithProps(id, props, checkHealth)).mapTo[ActorRef]
    }
  }

  /**
   * Wrapper that allows services add commands to the command manager with a single command
   *
   * @param id id of the Command you want to add, should be unique as it's used to call the Command after
   * @param checkHealth should this command have heath checks
   * @return Reference to the newly created Command Actor
   */
  def addCommand[T:ClassTag](id: String, actorClass:Class[T], checkHealth: Boolean = false) : Future[ActorRef] = {
    implicit val timeout: Timeout = Timeout(4.seconds)

    initCommandManager flatMap { cm =>
      (cm ? AddCommand(id, actorClass, checkHealth)).mapTo[ActorRef]
    }
  }

  /**
   * Wrapper that allows services execute commands (remote or otherwise)
   *
   * @param id name of the command you want to execute
   *             if this is a remote command the name will be the reference to the
   *             command
   * @param bean the bean that will be passed to the command
   * @param server If none then we are executing a local command, if set then it is a remote command and that is the server name
   * @param port The port of the remote server defaults to 0, as by default this function deals with local commands
   * @return Result of executing this Command
   */
  def executeCommand[Input<: Product : ClassTag, Output <: Any : ClassTag](id: String, bean: Input, server:Option[String]=None,
                                                            port:Int=2552)(implicit timeout:Timeout) : Future[Output] = {
    initCommandManager flatMap { _ =>
      CommandHelper.executeCommand[Input, Output](id, bean, server, port, commandManager)
    }
  }
}
