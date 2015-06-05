package com.webtrends.harness.component.spray.websocket

import java.io.ByteArrayInputStream

import akka.actor.Actor.Receive
import akka.io.Tcp
import spray.can.websocket.frame.{TextFrameStream, TextFrame, BinaryFrame}
import spray.http.HttpRequest

/**
 * Created by wallinm on 6/4/15.
 */
class ServerWorkerBean extends WebSocketWorker {

  override def businessLogic = ({
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case x: TextFrame =>
      log.info("Server TextFrame Received:" + x)
      bean match {
        case Some(b) =>
          sender() ! TextFrame(s"${x.payload.utf8String} ver:${b.getValue[String]("ver").get} account:${b.getValue[String]("account").get}")
        case None => sender() ! TextFrame("")

      }

    case x: HttpRequest => // do something
      log.info("Server HttpRequest Received")

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")
  }: Receive) orElse super.businessLogic

}
