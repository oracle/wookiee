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

package com.oracle.infy.wookiee.component.zookeeper.discoverable

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.command.{AddCommand, AddCommandWithProps, AddCommands, CommandFactory, CommandHelper}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait DiscoverableCommandHelper extends CommandHelper with Discoverable {
  this: Actor =>
  import context.dispatcher
  implicit val basePath: String

  /**
    * Used to register a new Endpoint that is accessible from other Services via `executeRemoteCommand`
    *
    * @param id name of the command you want to add, used to reference it later or from other Services
    * @param businessLogic the main business logic of the Command itself, will be executed each time
    */
  def addDiscoverableEndpoint[U <: Product: ClassTag, V: ClassTag](
      id: String,
      businessLogic: U => Future[V]
  ): Future[ActorRef] = {
    val props = CommandFactory.createCommand(businessLogic)
    addAndMakeDiscoverable(AddCommandWithProps(id, props))
  }

  /**
    * Wrapper that allows services to add commands to the command manager with a single discoverable command
    *
    * @param id name of the command you want to add, Wookiee v2 Note: Use what was in `name` field for `id`
    * @param props the props for that command actor class
    */
  def addDiscoverableCommandWithProps(id: String, props: Props): Future[ActorRef] =
    addAndMakeDiscoverable(AddCommandWithProps(id, props))

  /**
    * Wrapper that allows services add commands to the command manager with a single discoverable command
    *
    * @param id name of the command you want to add, Wookiee v2 Note: Use what was in `name` field for `id`
    * @param actorClass the class for the actor
    */
  def addDiscoverableCommand[T: ClassTag](id: String, actorClass: Class[T]): Future[ActorRef] =
    addAndMakeDiscoverable(AddCommand(id, actorClass))

  private def addAndMakeDiscoverable(addCommand: AddCommands): Future[ActorRef] = {
    implicit val timeout: Timeout = Timeout(5.seconds)

    initCommandManager flatMap { cm =>
      (cm ? addCommand).mapTo[ActorRef] map { ref =>
        makeDiscoverable(basePath, addCommand.id)
        ref
      }
    }
  }
}
