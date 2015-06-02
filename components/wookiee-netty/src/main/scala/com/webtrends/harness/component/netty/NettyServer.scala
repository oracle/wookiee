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

import akka.actor.{Actor, ActorRef}
import com.webtrends.harness.component.netty.config.NettyServerSettings

/**
 * Created by wallinm on 12/5/14.
 */
trait NettyServer { this : Actor =>
  var nettyServer:Option[ActorRef] = None

  def startNettyServer(settings:NettyServerSettings):ActorRef = {
    nettyServer = Some(context.actorOf(CoreNettyServer.props(settings), NettyServer.Name))
    nettyServer.get ! StartServer
    nettyServer.get
  }

  def stopNettyServer = {
    nettyServer match {
      case Some(s) => s ! ShutdownServer
      case None => //ignore
    }
  }
}

object NettyServer {
  val Name = "netty-server"
}
