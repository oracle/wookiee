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

import akka.actor.Actor
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.{SystemExtension, WookieeServiceDetails}
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}

import scala.concurrent.Future

/**
  * @author Michael Cuthbert and Spencer Wood
  */
trait Discoverable {
  this: Actor =>

  import context.system
  private lazy val service = DiscoverableService()

  def queryForNames(basePath: String)(implicit timeout: Timeout): Future[List[String]] =
    service.queryForNames(basePath)

  def queryForInstances(basePath: String, id: String)(
      implicit timeout: Timeout
  ): Future[List[ServiceInstance[WookieeServiceDetails]]] =
    service.queryForInstances(basePath, id)

  def makeDiscoverable(basePath: String, id: String)(implicit timeout: Timeout): Future[Boolean] =
    makeDiscoverable(basePath, id, new UriSpec(SystemExtension.getAddress(context.system)))

  def makeDiscoverable(basePath: String, id: String, uriSpec: UriSpec)(implicit timeout: Timeout): Future[Boolean] =
    service.makeDiscoverable(basePath, id, uriSpec)

  def getInstances(basePath: String, id: String)(
      implicit timeout: Timeout
  ): Future[List[ServiceInstance[WookieeServiceDetails]]] =
    service.getAllInstances(basePath, id)

  def getInstance(basePath: String, id: String)(
      implicit timeout: Timeout
  ): Future[ServiceInstance[WookieeServiceDetails]] =
    service.getInstance(basePath, id)

  def updateWeight(weight: Int, basePath: String, id: String, forceSet: Boolean = false)(
      implicit timeout: Timeout
  ): Future[Boolean] =
    service.updateWeight(weight, basePath, id, forceSet)
}
