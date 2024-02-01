package com.oracle.infy.wookiee.component.web.ws

import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter.REQUEST_HEADERS
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ThreadUtil

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.websocket._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

object WookieeWebsocket extends LoggingAdapter {
  private[oracle] val ec: ExecutionContext = ThreadUtil.createEC("wookiee-websocket-ec")

  /**
    * Call this to manually stop when done with this websocket, will automatically be called if connection is severed
    *
    * The optional `closeReason` will return a code (defaults to 1000) and message to the client. Available codes correspond
    * to Websocket standards and can be found here: [[javax.websocket.CloseReason.CloseCodes]]
    */
  def close(closeReason: Option[(String, Int)] = None)(implicit session: Session): Unit = closeReason match {
    case None =>
      log.info("DEBUG : WWS : Closing without close reason")
      session.close()
    case Some((message, code)) =>
      log.info(s"DEBUG : WWS : Closing with reason ${message}")
      session.close(
        new CloseReason(
          scala.util.Try(CloseReason.CloseCodes.getCloseCode(code)).getOrElse(CloseReason.CloseCodes.NORMAL_CLOSURE),
          message
        )
      )
  }
}

// Main class to extend for creating a websocket endpoint, pass this along to WookieeEndpoints.registerWebsocket
abstract class WookieeWebsocket[Auth <: Any: ClassTag] extends WookieeMonitor {
  /* OVERRIDEABLE METHODS */

  def path: String // WS path to host this endpoint, segments starting with '$' will be treated as wildcards

  def endpointType: EndpointType // Host this endpoint on internal or external port?

  def endpointOptions: EndpointOptions = EndpointOptions.default // Set of options including CORS allowed headers

  // Called when a new session is opened, can be used for authentication
  // If an error is thrown or the Future fails then we'll close the session right away
  def handleAuth(request: WookieeRequest): Future[Option[Auth]] =
    Future.successful(None)

  // Will be automatically called after the session is closed
  def onClosing(auth: Option[Auth]): Unit = ()

  // Main handler for incoming messages
  def handleText(text: String, request: WookieeRequest, authInfo: Option[Auth])(implicit session: Session): Unit

  def handlePongMessage(pong: PongMessage)(implicit session: Session): Unit = {
    // Ignore Pong message
  }

  // Called when any error occurs during handleText
  def handleError(request: WookieeRequest, message: String, authInfo: Option[Auth])(
      implicit session: Session
  ): Throwable => Unit = { throwable: Throwable =>
    log.error(s"WWS500: Error handling websocket message to path [$path]", throwable)
  }

  /* USER UTILITY METHODS */

  // Call this to send back a message to the client
  def reply(message: String)(implicit session: Session): Unit =
    session.getBasicRemote.sendText(message)

  def sendPing()(implicit session: Session): Unit = {
    if (session.isOpen)
      session.getBasicRemote.sendPing(ByteBuffer.wrap("Ping".getBytes))
  }

  def schedulePing(delay: FiniteDuration)(implicit session:Session): Unit = {
    schedule(delay, delay, new Runnable {
      def run(): Unit = {
        sendPing()
      }
    })
  }

  // Call this to close the current websocket session
  def close(closeReason: Option[(String, Int)] = None)(implicit session: Session): Unit = {
    log.info(s"DEBUG WW(WookieeWebsocket) : Closing ws due to ${closeReason}")
    WookieeWebsocket.close(closeReason)
  }

  /* INTERNAL ONLY */

  def getEndpointInstance: Endpoint = new InternalEndpoint

  // A new instance of this is created for each new websocket session
  protected[oracle] class InternalEndpoint extends Endpoint {
    val authInfo: AtomicReference[Option[Auth]] = new AtomicReference[Option[Auth]](None)

    override def onClose(session: Session, closeReason: CloseReason): Unit = {
      log.info(s"DEBUG WW(WookieeWebsocket - onClose method) : Closing ws due to ${closeReason}")
      onClosing(authInfo.get())
    }

    // Will forward messages on to the handleText method
    override def onOpen(session: Session, config: EndpointConfig): Unit = {
      try {
        implicit val ec: ExecutionContext = WookieeWebsocket.ec

        val headers = config.getUserProperties.asScala.toMap.apply(REQUEST_HEADERS).asInstanceOf[Headers]

        val allParamsRaw = session.getRequestParameterMap.asScala
        val allParams = allParamsRaw.toMap.map { case (k, v) => (k, v.asScala.mkString(",")) }

        // Separate out query params vs path params
        val (pathParams, queryParams) = allParams.partition { case (key, _) => pathKeys.contains(key) }

        val wookieeRequest = WookieeRequest(
          Content(""), // Empty as we don't have a request body
          pathParams,
          queryParams,
          headers
        )

        // The session may be closed during this call if auth fails
        handleAuth(wookieeRequest).map { auth =>
          authInfo.set(auth)

          // Register this endpoint as a message handler for text messages
          log.info(s"DEBUG : WM : Session here is ${session.getMaxIdleTimeout}");
          session.setMaxIdleTimeout(Long.MaxValue)
          log.info(s"DEBUG : WM : Session is not set to ${session.getMaxIdleTimeout}");
          session.addMessageHandler(new MessageHandler.Whole[String] {
            override def onMessage(message: String): Unit = {
              log.info(s"DEBUG : WM : Handling message ${message}")
              try {
                handleText(message, wookieeRequest, auth)(session)
              } catch {
                case e: Throwable =>
                  log.error(
                    s"WWS502: Error handling websocket on path [$path], request [$wookieeRequest], message [$message]",
                    e
                  )
                  handleError(wookieeRequest, message, auth)(session)(e)
              }
            }
          })
        } recover {
          case e: Exception =>
            log.error(s"WWS400: Error authenticating websocket connection to path [$path]", e)
            val closeReason = new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage)
            Try(session.close(closeReason))
            ()
        }
      } catch {
        case e: Exception =>
          log.error(s"WWS401: Error opening websocket connection to path [$path]", e)
          val closeReason = new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage)
          Try(session.close(closeReason))
      }
      ()
    }
  }

  // List of segments in `path` that start with '$
  lazy val pathKeys: List[String] = path.split("/").filter(_.nonEmpty).filter(_.startsWith("$")).map(_.drop(1)).toList

}
