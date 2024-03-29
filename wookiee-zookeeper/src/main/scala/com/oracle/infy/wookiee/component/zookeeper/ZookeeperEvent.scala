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
package com.oracle.infy.wookiee.component.zookeeper

import akka.actor.{Actor, ActorRef}
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperEvent.Internal.{
  RegisterZookeeperEvent,
  UnregisterZookeeperEvent
}
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperService.getMediator
import org.apache.curator.framework.recipes.cache.{ChildData, CuratorCacheListener, PathChildrenCacheEvent}
import org.apache.curator.framework.state.ConnectionState

object ZookeeperEvent {

  /**
    * Marker interface for zookeeper registration types.
    */
  sealed trait ZookeeperEventRegistration {
    def registrar: ActorRef
  }

  /**
    * Register for Zookeeper state changes
    */
  @SerialVersionUID(1L) case class ZookeeperStateEventRegistration(registrar: ActorRef)
      extends ZookeeperEventRegistration

  /**
    * Register for changes in Zookeeper children on a given path
    * @param path the path to watch
    * @param namespace the optional namespace
    */
  @SerialVersionUID(1L) case class ZookeeperChildEventRegistration(
      registrar: ActorRef,
      path: String,
      namespace: Option[String] = None
  ) extends ZookeeperEventRegistration

  /**
    * Register for changes in Zookeeper leader events on a given path
    * @param path the path to watch
    * @param namespace the optional namespace
    */
  @SerialVersionUID(1L) case class ZookeeperLeaderEventRegistration(
      registrar: ActorRef,
      path: String,
      namespace: Option[String] = None
  ) extends ZookeeperEventRegistration

  /**
    * Marker interface for zookeeper domain events.
    */
  sealed trait ZookeeperEvent

  /**
    * The state of the Zookeeper connection has changed
    * @param state the state information
    */
  @SerialVersionUID(1L) case class ZookeeperStateEvent(state: ConnectionState) extends ZookeeperEvent

  /**
    * An event occurred for a child on a path we are watching
    * @param event the event information
    */
  @SerialVersionUID(1L) case class ZookeeperChildEvent(
      `type`: CuratorCacheListener.Type,
      oldData: ChildData,
      newData: ChildData
  ) extends ZookeeperEvent

  /**
    * A leadership event has occurred
    */
  @SerialVersionUID(1L) case class ZookeeperLeadershipEvent(leader: Boolean) extends ZookeeperEvent

  private[oracle] object Internal {

    @SerialVersionUID(1L) case class RegisterZookeeperEvent(registrar: ActorRef, to: ZookeeperEventRegistration)

    @SerialVersionUID(1L) case class UnregisterZookeeperEvent(registrar: ActorRef, to: ZookeeperEventRegistration)

  }

}

trait ZookeeperEventAdapter {
  this: Actor =>

  import ZookeeperEvent.ZookeeperEventRegistration
  import context.system

  /**
    * Register for Zookeeper events.
    * @param registrar the actor that is to receive the events
    * @param to the class to register for
    */
  def register(registrar: ActorRef, to: ZookeeperEventRegistration): Unit =
    getMediator(system) ! RegisterZookeeperEvent(registrar, to)

  /**
    * Unregister for Zookeeper events.
    * @param registrar the actor that is to receive the events
    * @param to the class to register for
    */
  def unregister(registrar: ActorRef, to: ZookeeperEventRegistration): Unit =
    getMediator(system) ! UnregisterZookeeperEvent(registrar, to)
}
