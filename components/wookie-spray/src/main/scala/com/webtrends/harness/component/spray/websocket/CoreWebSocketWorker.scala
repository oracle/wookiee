package com.webtrends.harness.component.spray.websocket

import java.net.URLEncoder

import akka.actor.{ActorRef, Props}
import com.webtrends.harness.health.ActorHealth
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

/**
 * Created by wallinm on 3/27/15.
 */
class CoreWebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with ActorHealth with websocket.WebSocketServerWorker {
  override def receive = health orElse handshaking orElse closeLogic

  var worker: Option[ActorRef] = None

  override def handshaking: Receive = {
    // when a client request for upgrading to websocket comes in, we send
    // UHttp.Upgrade to upgrade to websocket pipelines with an accepting response.
    case websocket.HandshakeRequest(state) =>
      // Check to see if the uri has a registered worker
      // if not close the connection
      val path = state.request.uri.path.tail
      val props = WebSocketManager.getWorker(path.toString())
      props match {
        case Some(p) =>
          worker = Some(context.actorOf(p, URLEncoder.encode(path.toString(), "UTF-8")))
          state match {
            case wsFailure: websocket.HandshakeFailure => sender() ! wsFailure.response
            case wsContext: websocket.HandshakeContext => sender() ! UHttp.UpgradeServer(websocket.pipelineStage(self, wsContext), wsContext.response)
          }
        case None =>
          log.info(s"Received unsupported websocket connection request on [$path]")
          context.stop(self)
      }

    // upgraded successfully
    case UHttp.Upgraded =>
      context.become(health orElse businessLogic orElse closeLogic)
      self ! websocket.UpgradedToWebSocket // notify Upgraded to WebSocket protocol
  }

  def businessLogic: Receive = {
    case Push(msg) =>
      send(TextFrame(msg))
    case ev: Http.ConnectionClosed =>
      worker match {
        case Some(w) =>
          w ! ev
        case None =>  // Error
      }
      context.stop(self)
    case x =>
      worker match {
        case Some(w) =>
          w.tell(x, sender)
        case None =>   // Error
      }
  }
}

object CoreWebSocketWorker {
  def props(serverConnection: ActorRef) = Props(classOf[CoreWebSocketWorker], serverConnection)
}
