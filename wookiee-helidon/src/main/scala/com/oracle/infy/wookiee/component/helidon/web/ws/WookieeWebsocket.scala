package com.oracle.infy.wookiee.component.helidon.web.ws

import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.REQUEST_HEADERS
import com.oracle.infy.wookiee.component.helidon.web.util.WebUtil
import com.oracle.infy.wookiee.health.WookieeMonitor

import javax.websocket._
import scala.jdk.CollectionConverters._
import scala.util.Try

abstract class WookieeWebsocket extends Endpoint with WookieeMonitor {
  def path: String
  def endpointType: EndpointType
  def endpointOptions: EndpointOptions = EndpointOptions.default

  // Main handler for incoming messages
  def handleText(text: String, request: WookieeRequest)(implicit session: Session): Unit

  // Call this to send back a message to the client
  def sendText(message: String)(implicit session: Session): Unit =
    session.getBasicRemote.sendText(message)

  // Internal-only, will forward messages on to the handleText method
  override def onOpen(session: Session, config: EndpointConfig): Unit =
    try {
      val pathParams = session.getPathParameters.asScala.toMap
      val queryString = Option(session.getRequestURI.getQuery)
      // TODO Ensure these are read in correctly
      val queryParams = queryString.map(WebUtil.getQueryParams).getOrElse(Map.empty)
      val headers = config.getUserProperties.asScala.toMap.get(REQUEST_HEADERS) match {
        case Some(h: Headers) => h
        case _                => Headers()
      }

      val wookieeRequest = WookieeRequest(
        Content(""), // Empty as we don't have a request body
        pathParams,
        queryParams,
        headers
      )

      // Register this endpoint as a message handler for text messages
      session.addMessageHandler(new MessageHandler.Whole[String] {
        override def onMessage(message: String): Unit = {
          handleText(message, wookieeRequest)(session)
        }
      })
    } catch {
      case e: Exception =>
        log.error(s"WWS400: Error opening websocket connection to path [$path]", e)
        val closeReason = new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage)
        Try(session.close(closeReason))
        throw e
    }
}
