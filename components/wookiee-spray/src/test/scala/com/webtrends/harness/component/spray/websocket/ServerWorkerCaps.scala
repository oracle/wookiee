package com.webtrends.harness.component.spray.websocket

import java.io.ByteArrayInputStream

import akka.io.Tcp
import spray.can.websocket.frame.{BinaryFrame, TextFrame, TextFrameStream}
import spray.http.HttpRequest

/**
 * Created by wallinm on 4/17/15.
 */
class ServerWorkerCaps extends WebSocketWorker {

  override def businessLogic = ({
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case x: TextFrame =>
      val bs = x.payload.decodeString("UTF8")
      val u = bs.toUpperCase()
      if (x.payload.length <= 10) {
        log.info("Server TextFrame Received:" + x)
        sender() ! TextFrame(u)
      } else {
        log.info("Server Large TextFrame Received:" + x)
        sender() ! TextFrameStream(1, new ByteArrayInputStream(u.getBytes()))
      }

    case x: HttpRequest => // do something
      log.info("Server HttpRequest Received")

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")
  }: Receive) orElse super.businessLogic
}
