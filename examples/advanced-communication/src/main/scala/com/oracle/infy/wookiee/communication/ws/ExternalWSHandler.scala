package com.oracle.infy.wookiee.communication.ws

import com.oracle.infy.wookiee.component.web.http.HttpObjects
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.ws.WookieeWebsocket

import javax.websocket.Session
import scala.concurrent.{ExecutionContext, Future}

case class AuthHolder(token: String, userId: String)

// This is an example of a websocket handler that will be hosted on both internal and external ports
class ExternalWSHandler(implicit ec: ExecutionContext) extends WookieeWebsocket[AuthHolder] {
  // WS path to host this endpoint, segments starting with '$' will be treated as wildcards
  def path: String = "/ws/$userId"
  // Host this endpoint on internal and external ports
  def endpointType: EndpointType = EndpointType.BOTH

  // This is an example of how to set default headers for all incoming requests
  override def endpointOptions: HttpObjects.EndpointOptions = {
    super
      .endpointOptions
      .copy(defaultHeaders = HttpObjects.Headers(Map("Default-Wookiee-Header" -> List("Default Header Value"))))
  }

  // Main method to handle incoming messages
  override def handleText(text: String, request: HttpObjects.WookieeRequest, authInfo: Option[AuthHolder])(
      implicit session: Session
  ): Unit = {
    log.info(s"Received message [$text] from user [${authInfo.map(_.userId).getOrElse("no-auth")}]")

    // Throw an error on purpose for demonstration
    if (text == "error") {
      log.warn(s"User [${authInfo.get.userId}] sent an error message")
      throw new Exception("User sent an error message")
    }

    if (text == "close") {
      log.warn(s"User [${authInfo.get.userId}] sent a close message")
      session.close()
      return
    }

    reply(
      s"Received message [$text] from user [${authInfo.map(_.userId).getOrElse("no-auth")}], " +
        s"with auth token [${authInfo.map(_.token).getOrElse("no-auth")}]"
    )
  }

  // Send along 'token' as a query parameter to authenticate
  override def handleAuth(request: HttpObjects.WookieeRequest): Future[Option[AuthHolder]] = Future {
    val userId = request.pathSegments("userId")
    // If userId was 'bad-user' we'll fail and close the session
    if (userId == "bad-user") {
      log.warn(s"User [bad-user] is not authenticated")
      throw new IllegalArgumentException("User [bad-user] is not authenticated")
    }

    Some(
      request
        .queryParameters
        .get("token")
        .map(AuthHolder(_, userId))
        .getOrElse(AuthHolder("no-auth", userId))
    )
  }

  override def onClosing(auth: Option[AuthHolder]): Unit = {
    log.info(s"DEBUG : WW(ExternalESHandler) Closing websocket session from user [${auth.map(_.userId).getOrElse("no-auth")}]")
    log.info(s"Closing websocket session from user [${auth.map(_.userId).getOrElse("no-auth")}]")
  }

  override def handleError(request: HttpObjects.WookieeRequest, message: String, authInfo: Option[AuthHolder])(
      implicit session: Session
  ): Throwable => Unit = {
    case _: IllegalArgumentException =>
      WookieeWebsocket.close(Some(("You must be authenticated to use this websocket", 1008)))
    case _: Throwable =>
      log.error(s"Unexpected error handling websocket message to path [$path], skipping message")
      reply("Unexpected error handling websocket message, skipping message")
  }

}
