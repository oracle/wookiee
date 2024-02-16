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

import com.oracle.infy.wookiee.Mediator
import org.apache.zookeeper.CreateMode

object ZookeeperService extends Mediator[ZookeeperActor] {

  @SerialVersionUID(1L) private[oracle] case class SetPathData(
      path: String,
      data: Array[Byte],
      create: Boolean = false,
      ephemeral: Boolean = false,
      namespace: Option[String] = None,
      async: Boolean = false
  )

  @SerialVersionUID(1L) private[oracle] case class GetPathData(path: String, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[oracle] case class GetOrSetPathData(
      path: String,
      data: Array[Byte],
      ephemeral: Boolean = false,
      namespace: Option[String] = None
  )

  @SerialVersionUID(1L) private[oracle] case class GetPathChildren(
      path: String,
      includeData: Boolean,
      namespace: Option[String] = None
  )

  @SerialVersionUID(1L) private[oracle] case class GetRegistrationPath()

  @SerialVersionUID(1L) private[oracle] case class CreateNode(
      path: String,
      createMode: CreateMode,
      data: Option[Array[Byte]],
      namespace: Option[String] = None
  )

  @SerialVersionUID(1L) private[oracle] case class CreateCounter(path: String)

  @SerialVersionUID(1L) private[oracle] case class GetNodeExists(path: String, namespace: Option[String] = None)

  @SerialVersionUID(1L) private[oracle] case class DeleteNode(path: String, namespace: Option[String] = None)
}
