package com.webtrends.harness.component.spray.websocket

import akka.actor.{ActorRef, ActorSystem}
import akka.io.IO
import spray.can.server.UHttp
import spray.can.websocket.{UpgradedToWebSocket, SendStream, Send}
import spray.can.websocket.frame.Frame
import spray.can.{websocket, Http}
import spray.http.HttpRequest

/**
 * Created by wallinm on 4/22/15.
 */
class ClientWorker(connect: Http.Connect, val upgradeRequest: HttpRequest) extends websocket.WebSocketClientWorker {
  implicit val system = ActorSystem()

  var commander: Option[ActorRef] = None

  IO(UHttp) ! connect

  def businessLogic: Receive =
  {
    case Send(frame) =>
      commander = Some(sender())
      connection ! frame
    case SendStream(frame) =>
      commander = Some(sender())
      connection ! frame
    case frame: Frame =>
      commander match {
        case Some(ref) => ref ! frame
        case None =>
      }
    case UpgradedToWebSocket =>
      log.info("Client upgraded to WebSocket")
  }
}

