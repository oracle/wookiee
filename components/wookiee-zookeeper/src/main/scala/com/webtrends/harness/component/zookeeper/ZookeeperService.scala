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
package com.webtrends.harness.component.zookeeper

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.Internal.{RegisterZookeeperEvent, UnregisterZookeeperEvent}
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.ZookeeperEventRegistration

import scala.concurrent.Future

private[harness] class ZookeeperService()(implicit system: ActorSystem) {

  import ZookeeperService._

  private[zookeeper] val defaultTimeout = Timeout(system.settings.config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  private val log = Logging(system, this.getClass)

  /**
   * Register for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def register(registrar: ActorRef, to: ZookeeperEventRegistration): Unit =
    mediator.get ! RegisterZookeeperEvent(registrar, to)

  /**
   * Unregister for subscription events. This is not used for maintaining
   * subscriptions, but can be used more for testing subscription events.
   * @param registrar the actor that is to receive the events
   * @param to the class to register for
   */
  def unregister(registrar: ActorRef, to: ZookeeperEventRegistration): Unit =
    mediator.foreach( _ ! UnregisterZookeeperEvent(registrar, to))

  /**
   * Set data in Zookeeper for the given path
   * @param path the path to set data in
   * @param data the data to set
   * @param create should the node be created if it does not exist
   * @param ephemeral should the created node be ephemeral
   * @param namespace an optional name space
   * @return the length of data that was written
   */
  def setData(path: String, data: Array[Byte], create: Boolean = false, ephemeral: Boolean = false, namespace: Option[String] = None)
             (implicit timeout: akka.util.Timeout = defaultTimeout): Future[Int] =
    (mediator.get ? SetPathData(path, data, create, ephemeral, namespace)).mapTo[Int]

  /**
   * Set data in Zookeeper for the given path async, does not return anything
   * @param path the path to set data in
   * @param data the data to set
   * @param create should the node be created if it does not exist
   * @param ephemeral should the created node be ephemeral
   * @param namespace an optional name space
   */
  def setDataAsync(path: String, data: Array[Byte], create: Boolean = false, ephemeral: Boolean = false, namespace: Option[String] = None)
                  (implicit timeout: akka.util.Timeout = defaultTimeout) =
    mediator.get ! SetPathData(path, data, create, ephemeral, namespace, true)

  /**
   * Get Zookeeper data for the given path
   * @param path the path to get data from
   * @param namespace an optional name space
   * @return An instance of Array[Byte] or an empty array
   */
  def getData(path: String, namespace: Option[String] = None)(implicit timeout: akka.util.Timeout = defaultTimeout): Future[Array[Byte]] = {
    (mediator.get ? GetPathData(path, namespace)).mapTo[Array[Byte]]
  }

  /**
   * Get the data in Zookeeper for the given path or set it if the path does not exist
   * @param path the path to set data in
   * @param data the data to set
   * @param ephemeral should the created node be ephemeral
   * @param namespace an optional name space
   * @return An instance of Array[Byte] or an empty array
   */
  def getOrSetData(path: String, data: Array[Byte], ephemeral: Boolean = false, namespace: Option[String] = None)
                  (implicit timeout: akka.util.Timeout = defaultTimeout): Future[Array[Byte]] =
    (mediator.get ? GetOrSetPathData(path, data, ephemeral, namespace)).mapTo[Array[Byte]]

  /**
   * Get the child nodes for the given path
   * @param path the path to get the children of
   * @param includeData should the children's data be returned. Defaults to false.
   * @param namespace an optional name space
   * @return A Seq[String] or Nil is an error occurs or if there no children
   */
  def getChildren(path: String, includeData: Boolean = false, namespace: Option[String] = None)
                 (implicit timeout: akka.util.Timeout = defaultTimeout): Future[Seq[(String, Option[Array[Byte]])]] = {
    (mediator.get ? GetPathChildren(path, includeData, namespace)).mapTo[Seq[(String, Option[Array[Byte]])]]
  }

  /**
   * Create a node at the given path
   * @param path the path to create the node at
   * @param ephemeral is the node ephemeral
   * @param data the data to set in the node
   * @param namespace an optional name space
   * @return the full path to the newly created node or an empty string if an error occurred
   */
  def createNode(path: String, ephemeral: Boolean, data: Option[Array[Byte]], namespace: Option[String] = None)
                (implicit timeout: akka.util.Timeout = defaultTimeout): Future[String] = {
    (mediator.get ? CreateNode(path, ephemeral, data, namespace)).mapTo[String]
  }

  /**
   * Delete a node at the given path
   * @param path the path to delete the node at
   * @param namespace an optional name space
   * @return the full path to the newly created node or an empty string if an error occurred
   */
  def deleteNode(path: String, namespace: Option[String] = None)
                (implicit timeout: akka.util.Timeout = defaultTimeout): Future[String] = {
    (mediator.get ? DeleteNode(path, namespace)).mapTo[String]
  }

  /**
   * Does the node exist
   * @param path the path to check
   * @param namespace an optional name space
   * @return true or false
   */
  def nodeExists(path: String, namespace: Option[String] = None)
                (implicit timeout: akka.util.Timeout = defaultTimeout): Future[Boolean] = {
    (mediator.get ? GetNodeExists(path, namespace)).mapTo[Boolean]
  }
}

object ZookeeperService {

  def apply()(implicit system: ActorSystem): ZookeeperService = new ZookeeperService

  private var mediator: Option[ActorRef] = None

  private[harness] def registerMediator(actor: ActorRef) = {
    mediator = Some(actor)
  }

  private[harness] def unregisterMediator(actor: ActorRef) = {
    mediator = None
  }

  @SerialVersionUID(1L) private[harness] case class SetPathData(path: String, data: Array[Byte],
                                                                create: Boolean = false, ephemeral: Boolean = false, namespace: Option[String] = None, async: Boolean = false)

  @SerialVersionUID(1L) private[harness] case class GetPathData(path: String, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[harness] case class GetOrSetPathData(path: String, data: Array[Byte],
                                                                       ephemeral: Boolean = false, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[harness] case class GetPathChildren(path: String, includeData: Boolean, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[harness] case class CreateNode(path: String, ephemeral: Boolean, data: Option[Array[Byte]], namespace: Option[String] = None)

  @SerialVersionUID(1L) private[harness] case class GetNodeExists(path: String, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[harness] case class DeleteNode(path: String, namespace: Option[String] = None)
}
