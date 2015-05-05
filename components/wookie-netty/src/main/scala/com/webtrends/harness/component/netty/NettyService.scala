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

package com.webtrends.harness.component.netty

import com.webtrends.harness.component.netty.handler.HandlerManager
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import io.netty.channel.ChannelHandler

/**
 * Created by wallinm on 12/10/14.
 */
trait NettyService extends Service {

  def getHandlers(): Map[String, ChannelHandler] = {
    Map[String, ChannelHandler]().empty
  }

  override def serviceReceive = {
    // This is called by the harness itself in order to get internal details of the service
    case GetMetaDetails =>
      HandlerManager.addGetHandlers(self.path.name, getHandlers)
      // Get the service's meta data
      sender() ! getMetaDetails
  }: Receive

  /**
   * The actor has been asked to respond with some additional meta information.
   * @return An instance of ServiceMetaDetails
   */
  protected def getMetaDetails: ServiceMetaDetails = {
    ServiceMetaDetails(getHandlers().size > 0)
  }
}
