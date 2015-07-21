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

import akka.actor.Actor

import scala.concurrent.Future

/**
 * @author Michael Cuthbert on 7/9/15.
 */
trait ZookeeperAdapter {
  this: Actor =>

  import this.context.system

  private lazy val zkService = ZookeeperService()

  /**
   * Set data in Zookeeper for the given path
   * @param path the path to set data in
   * @param data the data to set
   * @param create should the node be created if it does not exist
   * @param ephemeral should the created node be ephemeral
   * @return the length of data that was written
   */
  def setData(path: String, data: Array[Byte], create: Boolean = false, ephemeral: Boolean = false)
             (implicit timeout: akka.util.Timeout): Future[Int] = zkService.setData(path, data, create, ephemeral)

  /**
   * Set data in Zookeeper for the given path async, does not return anything
   * @param path the path to set data in
   * @param data the data to set
   * @param create should the node be created if it does not exist
   * @param ephemeral should the created node be ephemeral
   * @param namespace an optional name space
   */
  def setDataAsync(path: String, data: Array[Byte], create: Boolean = false, ephemeral: Boolean = false, namespace: Option[String] = None)
                  (implicit timeout: akka.util.Timeout) =
    zkService.setDataAsync(path, data, create, ephemeral, namespace)

  /**
   * Set data in Zookeeper for the given path and namespace
   * @param namespace the name space
   * @param path the path to set data in
   * @param data the data to set
   * @param create should the node be created if it does not exist
   * @param ephemeral should the created node be ephemeral
   * @return the length of data that was written
   */
  def setDataWithNamespace(namespace: String, path: String, data: Array[Byte], create: Boolean = false, ephemeral: Boolean = false)
                          (implicit timeout: akka.util.Timeout): Future[Int] = zkService.setData(path, data, create, ephemeral, Some(namespace))

  /**
   * Get Zookeeper data for the given path
   * @param path the path to get data from
   * @return An instance of Array[Byte] or an empty array
   */
  def getData(path: String)(implicit timeout: akka.util.Timeout): Future[Array[Byte]] = zkService.getData(path)

  /**
   * Get Zookeeper data for the given path
   * @param namespace the name space
   * @param path the path to get data from
   * @return An instance of Array[Byte] or an empty array
   */
  def getDataWithNamespace(namespace: String, path: String)(implicit timeout: akka.util.Timeout): Future[Array[Byte]] =
    zkService.getData(path, Some(namespace))

  /**
   * Get the data in Zookeeper for the given path or set it if the path does not exist
   * @param path the path to set data in
   * @param data the data to set
   * @param ephemeral should the created node be ephemeral
   * @return An instance of Array[Byte] or an empty array
   */
  def getOrSetData(path: String, data: Array[Byte], ephemeral: Boolean = false)
                  (implicit timeout: akka.util.Timeout): Future[Array[Byte]] = zkService.getOrSetData(path, data, ephemeral)

  /**
   * Get the data in Zookeeper for the given path or set it if the path does not exist
   * @param namespace the name space
   * @param path the path to set data in
   * @param data the data to set
   * @param ephemeral should the created node be ephemeral
   * @return An instance of Array[Byte] or an empty array
   */
  def getOrSetDataWithNamespace(namespace: String, path: String, data: Array[Byte], ephemeral: Boolean = false)
                               (implicit timeout: akka.util.Timeout): Future[Array[Byte]] = zkService.getOrSetData(path, data, ephemeral, Some(namespace))

  /**
   * Get the child nodes for the given path
   * @param path the path to get the children of
   * @param includeData should the children's data be returned. Defaults to false.
   * @return A Seq[String] or Nil is an error occurs or if there no children
   */
  def getChildren(path: String, includeData: Boolean = false)
                 (implicit timeout: akka.util.Timeout): Future[Seq[(String, Option[Array[Byte]])]] =
    zkService.getChildren(path, includeData)

  /**
   * Get the child nodes for the given path
   * @param namespace the name space
   * @param path the path to get the children of
   * @param includeData should the children's data be returned. Defaults to false.
   * @return A Seq[String] or Nil is an error occurs or if there no children
   */
  def getChildrenWithNamespace(namespace: String, path: String, includeData: Boolean = false)
                              (implicit timeout: akka.util.Timeout): Future[Seq[(String, Option[Array[Byte]])]] =
    zkService.getChildren(path, includeData, Some(namespace))

  /**
   * Does the node exist
   * @param path the path to check
   * @return true or false
   */
  def nodeExists(path: String)
                (implicit timeout: akka.util.Timeout): Future[Boolean] =
    zkService.nodeExists(path, None)

  /**
   * Does the node exist
   * @param namespace the name space
   * @param path the path to check
   * @return true or false
   */
  def nodeExistsWithNamespace(namespace: String, path: String)
                             (implicit timeout: akka.util.Timeout): Future[Boolean] =
    zkService.nodeExists(path, Some(namespace))

  /**
   * Create a node at the given path
   * @param path the path to create the node at
   * @param ephemeral is the node ephemeral
   * @param data the data to set in the node
   * @return the full path to the newly created node
   */
  def createNode(path: String, ephemeral: Boolean, data: Option[Array[Byte]])
                (implicit timeout: akka.util.Timeout): Future[String] = zkService.createNode(path, ephemeral, data)

  /**
   * Create a node at the given path
   * @param namespace the name space
   * @param path the path to create the node at
   * @param ephemeral is the node ephemeral
   * @param data the data to set in the node
   * @return the full path to the newly created node
   */
  def createNodeWithNamespace(namespace: String, path: String, ephemeral: Boolean, data: Option[Array[Byte]])
                             (implicit timeout: akka.util.Timeout): Future[String] = zkService.createNode(path, ephemeral, data, Some(namespace))

  /**
   * Delete a node at the given path
   * @param path the path to create the node at
   * @return the full path to the newly created node
   */
  def deleteNode(path: String)
                (implicit timeout: akka.util.Timeout): Future[String] = zkService.deleteNode(path)

  /**
   * Delete a node at the given path
   * @param namespace the name space
   * @param path the path to delete the node at
   * @return the full path to the newly created node
   */
  def deleteNodeWithNamespace(namespace: String, path: String)
                             (implicit timeout: akka.util.Timeout): Future[String] = zkService.deleteNode(path, Some(namespace))
}
