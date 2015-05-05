package com.webtrends.harness.component.spray

import akka.actor.{Actor, ActorRef}
import com.webtrends.harness.component.spray.websocket._
import spray.can.server.ServerSettings

/**
 * Created by wallinm on 4/3/15.
 */
trait SprayWebSocketServer { this : Actor =>
  var webSocketServer:Option[ActorRef] = None

  def startWebSocketServer(port: Int, settings:Option[ServerSettings]=None) : ActorRef = {
    webSocketServer = Some(context.actorOf(CoreWebSocketServer.props(port, settings), SprayWebSocketServer.Name))
    webSocketServer.get ! WebSocketBindServer
    webSocketServer.get
  }

  def stopWebSocketServer = {
    webSocketServer match {
      case Some(s) => s ! WebSocketShutdownServer
      case None => //ignore
    }
  }
}

object SprayWebSocketServer {
  val Name = "websocket-server"
}
