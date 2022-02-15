/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.routes

import akka.http.scaladsl.server.Route

trait RouteContainers {
  private val routes = collection.mutable.LinkedHashSet[Route]()

  def addRoute(r: Route): Unit = {
    routes.synchronized {
      routes.add(r)
    }
    ()
  }

  def isEmpty: Boolean = routes.isEmpty
  def getRoutes: List[Route] = routes.toList

  def clearRoutes(): Unit = {
    routes.synchronized {
      routes.clear()
    }
  }
}

object InternalAkkaHttpRouteContainer extends RouteContainers
object ExternalAkkaHttpRouteContainer extends RouteContainers
