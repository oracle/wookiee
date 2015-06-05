package com.webtrends.harness.component.spray.websocket

import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.CommandBean
import spray.can.{websocket, Http}
import spray.can.websocket.FrameCommandFailed

import scala.util.Success

final case class Push(msg: String)
final case class SetBean(bean: Option[CommandBean])

/**
 * Created by wallinm on 4/3/15.
 */
trait WebSocketWorker extends HActor {

  override def receive = health orElse businessLogic orElse closeLogic

  var bean: Option[CommandBean] = None
  def getCommandBean() = {
    bean match {
      case Some(b) => b
      case None => new CommandBean()
    }
  }

  def businessLogic: Receive = {
    case Push(msg) =>
      log.debug("Got message " + msg)
      context.parent ! Push(msg)

    case SetBean(b) =>
      bean = b
      sender() ! Success

    case x: FrameCommandFailed =>
      log.error("Server frame command failed", x)

    case websocket.UpgradedToWebSocket =>
      log.debug("Server upgraded to WebSocket")

    case x => log.debug("Server received unknown message " + x)
  }

  def closeLogic: Receive = {
    case ev: Http.ConnectionClosed =>
      context.stop(self)
      log.debug("Server connection closed on event: {}", ev)
  }
}
