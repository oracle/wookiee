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

package com.webtrends.service

import com.webtrends.harness.component.netty.NettyService
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.Ready
import io.netty.channel.ChannelHandler

import scala.concurrent.Future

class NettyExample extends NettyService {

  /**
   * This is the receive expression
   *   Log when the Ready message is received
   */
  override def serviceReceive = ({
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
  }: Receive) orElse super.serviceReceive

  /**
   * Return the health of this service
   */
  override def getHealth: Future[HealthComponent] = {
    Future {
      HealthComponent("NettyExample", ComponentState.NORMAL, "NettyExample is running and ready to execute requests")
    }
  }

  override def getHandlers() = {
    Map[String,ChannelHandler]("NettyExampleHandler" ->  new NettyExampleHandler with NettyExampleWebSocket)
  }
}

