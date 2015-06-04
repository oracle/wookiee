package com.webtrends.harness.component.spray.websocket

import akka.pattern.ask
import akka.actor.{ActorRef, Props}
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.health.ActorHealth
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

import scala.util.{Failure, Success}

/**
 * Created by wallinm on 3/27/15.
 */
class CoreWebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with ActorHealth with websocket.WebSocketServerWorker {
  import scala.concurrent.ExecutionContext.Implicits.global
  override def receive = health orElse handshaking orElse closeLogic

  var worker: Option[ActorRef] = None
  var bean: Option[CommandBean] = None

  override def handshaking: Receive = {
    // when a client request for upgrading to websocket comes in, we send
    // UHttp.Upgrade to upgrade to websocket pipelines with an accepting response.
    case websocket.HandshakeRequest(state) =>
      // Check to see if the uri has a registered worker
      // if not close the connection
      val path = state.request.uri.path
      WebSocketManager.getWorker(path.toString()) match {
        case Some((props, b)) =>
          bean = b
          worker = Some(context.actorOf(props, props.actorClass().getSimpleName))
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
    worker match {
      case Some(w) =>
        w.ask(SetBean(bean)) onComplete {
          case Success (s) =>
            context.become(health orElse businessLogic orElse closeLogic)
            self ! websocket.UpgradedToWebSocket // notify Upgraded to WebSocket protocol
          case Failure (f) =>
            context.stop (self)
        }
      case None =>
        log.error(s"Websocket upgraded with no worker, closing connection")
        context.stop(self)
    }
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
