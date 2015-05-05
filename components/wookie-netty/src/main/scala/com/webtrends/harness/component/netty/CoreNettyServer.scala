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

import java.net.InetAddress

import akka.actor._
import akka.routing.FromConfig
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.netty.config.NettyServerSettings
import com.webtrends.harness.component.netty.handler.HandlerManager
import com.webtrends.harness.component.netty.server.SocketServer
import com.webtrends.harness.health._
import io.netty.channel.ChannelHandler

import scala.concurrent.Future

object CoreNettyServer {
  def props(settings:NettyServerSettings): Props = Props(classOf[CoreNettyServer], settings)
}

@SerialVersionUID(1L) case class StartServer()
@SerialVersionUID(1L) case class ShutdownServer()

class CoreNettyServer(settings:NettyServerSettings) extends HActor {
  var server: Option[SocketServer] = None
  var worker: Option[ActorRef] = None

  override def receive: Receive = starting

  def starting: Receive = {
    case StartServer => startServer
  }

  def running: Receive = health orElse {
    case ShutdownServer => shutdownServer
  }

  protected def startServer: Unit = {
    server = Some(SocketServer(settings, this))
    worker = Some(context.actorOf(FromConfig.props(Props[CoreNettyWorker]), "netty-worker"))
    context.become(running)
    context.parent ! NettyRunning
    log.info(s"Netty Http server bound to port ${settings.port}")
  }

  protected def shutdownServer: Unit = {
    server match {
      case Some(s) =>
        log.info(s"Trying to shutdown the netty server ${InetAddress.getLocalHost.getHostName} on port ${settings.port}")
        s.close()
      case None => // do nothing
    }
  }

  /**
   * Fetch routes from all registered services and concatenate with our default ones that
   * are defined below.
   */
  def getHandlers: Map[String, ChannelHandler] = {
    val serviceHandlers = HandlerManager.getHandlers.filter (r => ! r.equals (Map.empty) )
    serviceHandlers ++ getBaseHandlers
  }

  private def getBaseHandlers = {
    Map[String, ChannelHandler]("core-http-handler" -> new CoreHttpHandler(settings, worker.get) with WebSocket)
  }

  override protected def getHealth: Future[HealthComponent] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    
    Future {
      server match {
        case None =>
          HealthComponent(
            "netty-server",
            ComponentState.CRITICAL,
            "The internal netty server is not running",
            None)
        case Some(s) =>
          HealthComponent(
            "netty-server",
            ComponentState.NORMAL,
            s"The internal netty server is running on port ${settings.port}",
            None)
      }
    }
  }
}