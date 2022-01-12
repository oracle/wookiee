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

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.{WookieeServiceDetails, ZookeeperManager}
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperService._
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}

import scala.concurrent.Future

/**
  * @author Michael Cuthbert and Spencer Wood
  */
private[oracle] class DiscoverableService()(implicit system: ActorSystem) {
  import DiscoverableService._

  private[zookeeper] val defaultTimeout = Timeout(
    system
      .settings
      .config
      .getDuration(s"${ZookeeperManager.ComponentName}.default-send-timeout", TimeUnit.MILLISECONDS),
    TimeUnit.MILLISECONDS
  )

  def queryForNames(basePath: String)(implicit timeout: Timeout = defaultTimeout): Future[List[String]] =
    (getMediator(system) ? QueryForNames(basePath)).mapTo[List[String]]

  def queryForInstances(basePath: String, id: String)(
      implicit timeout: Timeout = defaultTimeout
  ): Future[List[ServiceInstance[WookieeServiceDetails]]] =
    (getMediator(system) ? QueryForInstances(basePath, id)).mapTo[List[ServiceInstance[WookieeServiceDetails]]]

  def makeDiscoverable(basePath: String, id: String, uriSpec: UriSpec)(
      implicit timeout: Timeout = defaultTimeout
  ): Future[Boolean] =
    (getMediator(system) ? MakeDiscoverable(basePath, id, uriSpec)).mapTo[Boolean]

  def getInstance(basePath: String, id: String)(
      implicit timeout: Timeout
  ): Future[ServiceInstance[WookieeServiceDetails]] = {
    (getMediator(system) ? GetInstance(basePath, id)).mapTo[ServiceInstance[WookieeServiceDetails]]
  }

  def getAllInstances(basePath: String, id: String)(
      implicit timeout: Timeout
  ): Future[List[ServiceInstance[WookieeServiceDetails]]] = {
    (getMediator(system) ? GetAllInstances(basePath, id)).mapTo[List[ServiceInstance[WookieeServiceDetails]]]
  }

  def updateWeight(weight: Int, basePath: String, id: String, forceSet: Boolean)(
      implicit timeout: Timeout
  ): Future[Boolean] = {
    (getMediator(system) ? UpdateWeight(weight, basePath, id, forceSet)).mapTo[Boolean]
  }
}

object DiscoverableService {
  def apply()(implicit system: ActorSystem): DiscoverableService = new DiscoverableService

  @SerialVersionUID(1L) private[oracle] case class QueryForNames(basePath: String)

  @SerialVersionUID(3L) private[oracle] case class UpdateWeight(
      weight: Int,
      basePath: String,
      id: String,
      forceSet: Boolean
  )

  @SerialVersionUID(2L) private[oracle] case class QueryForInstances(basePath: String, id: String)

  @SerialVersionUID(2L) private[oracle] case class MakeDiscoverable(basePath: String, id: String, uriSpec: UriSpec)

  @SerialVersionUID(2L) private[oracle] case class GetInstance(basePath: String, id: String)

  @SerialVersionUID(2L) private[oracle] case class GetAllInstances(basePath: String, id: String)
}
