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
package com.webtrends.harness.component.spray

import com.webtrends.harness.component.spray.route.RouteManager
import com.webtrends.harness.component.spray.websocket.WebSocketWorkerHelper
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import spray.routing.{Route, HttpService}

trait SprayService extends Service with HttpService with WebSocketWorkerHelper {

  def routes: Route = Map.empty

  override def preStart(): Unit = {
    initWebSocketWorkerHelper
    super[Service].preStart()
  }

  // To be defined in service actor
  override def serviceReceive = ({
    // This is called by the harness itself in order to get internal details of the service
    case GetMetaDetails =>
      RouteManager.addRoute(self.path.name, routes)
      // Get the services meta data
      sender() ! getMetaDetails
  }: Receive) orElse runRoute(routes)

  /**
   * The actor has been asked to respond with some additional meta information.
   * @return An instance of ServiceMetaDetails
   */
  protected def getMetaDetails: ServiceMetaDetails = {
    ServiceMetaDetails(!routes.equals(Map.empty))
  }
}
