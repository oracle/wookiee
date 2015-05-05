package com.webtrends.harness.component.spray.websocket

import java.io.ByteArrayInputStream

import akka.io.Tcp
import spray.can.websocket.frame.{TextFrameStream, BinaryFrame, TextFrame}
import spray.http.HttpRequest

/**
 * Created by wallinm on 4/17/15.
 */
class ServerWorker extends WebSocketWorker {

  override def businessLogic = ({
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case x: TextFrame =>
      if (x.payload.length <= 10) {
        log.info("Server TextFrame Received:" + x)
        sender() ! x
      } else {
        log.info("Server Large TextFrame Received:" + x)
        sender() ! TextFrameStream(1, new ByteArrayInputStream(x.payload.toArray))
      }

    case x: HttpRequest => // do something
      log.info("Server HttpRequest Received")

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")
  }: Receive) orElse super.businessLogic
}
