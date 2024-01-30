package com.oracle.infy.wookiee.component.web.ws

import com.oracle.infy.wookiee.component.web.http.HttpObjects.WookieeRequest
import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.nio.ByteBuffer
import javax.websocket.Session

// Class used in the functional interface for the websocket endpoint, allows ability to send messages
// and close the websocket. No need to worry about this class if using the WookieeWebsocket trait
class WebsocketInterface(
    val request: WookieeRequest
)(implicit val session: Session)
    extends LoggingAdapter {

  /**
    * Main method to send an message up to the websocket client,
    * any calls on this will bubble up one event, can be called many times
    */
  def reply(output: String): Unit = {
    session.getAsyncRemote.sendText(output)
    ()
  }

  def reply(byteBuffer: ByteBuffer): Unit = {
    session.getAsyncRemote.sendBinary(byteBuffer)
    ()
  }

  /**
    * Call this to close the websocket connection
    *
    * @param closeReason The optional `closeReason` will return a code (defaults to 1000) and message to the client. Available codes correspond
    *                    to Websocket standards and can be found here: [[javax.websocket.CloseReason.CloseCodes]]
    */
  def close(closeReason: Option[(String, Int)] = None): Unit = {
    log.info(s"DEBUG WW(WebsocketInterface) : Closing connection due to ${closeReason}")
    WookieeWebsocket.close(closeReason)
  }

  // The query parameters from the request (i.e. ?param1=value1&param2=value2)
  def getQueryParams: Map[String, String] = request.queryParameters

  // The path segments from the request (i.e. registry /ws/$segment1 and request /ws/value1 would yield Map("segment1" -> "value1")))
  def getPathSegments: Map[String, String] = request.pathSegments
}
