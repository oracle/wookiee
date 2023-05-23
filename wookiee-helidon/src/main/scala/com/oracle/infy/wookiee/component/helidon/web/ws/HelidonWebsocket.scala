package com.oracle.infy.wookiee.component.helidon.web.ws

import com.oracle.infy.wookiee.command.MapBean
import com.oracle.infy.wookiee.health.WookieeMonitor

import javax.websocket.{Endpoint, EndpointConfig, MessageHandler, Session}

abstract class HelidonWebsocket extends Endpoint with WookieeMonitor {

  override def onOpen(session: Session, config: EndpointConfig): Unit = {
    // Register this endpoint as a message handler for text messages
    session.addMessageHandler(new MessageHandler.Whole[String] {
      override def onMessage(message: String): Unit = {
        handleText(message, MapBean(Map()))
      }
    })
  }

  // Main handler for incoming messages
  def handleText(text: String, bean: MapBean): Unit

  def sendText(session: Session, message: String): Unit = {
    session.getBasicRemote.sendText(message)
  }
}
