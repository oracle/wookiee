package com.oracle.infy.wookiee.component.helidon.web.ws.tyrus

import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointOptions
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.FLUSH_BUFFER
import com.oracle.infy.wookiee.logging.LoggingAdapter
import io.helidon.webserver.tyrus.TyrusWriterPublisher
import io.helidon.webserver.{ServerRequest, ServerResponse}
import org.glassfish.tyrus.core.{RequestContext, TyrusUpgradeResponse}
import org.glassfish.tyrus.spi.WebSocketEngine

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import javax.websocket.server.HandshakeRequest
import scala.jdk.CollectionConverters._

class WookieeTyrusHandler(engine: WebSocketEngine) extends LoggingAdapter {

  def acceptWithContext(req: ServerRequest, res: ServerResponse, endpointOpts: EndpointOptions): Unit = {
    // Skip this handler if not an upgrade request
    val secWebSocketKey = req.headers().value(HandshakeRequest.SEC_WEBSOCKET_KEY)
    if (secWebSocketKey.isEmpty) {
      req.next()
      return
    }

    // Create Tyrus request context, copying headers and query params
    val paramsMap = new util.HashMap[String, Array[String]]()
    val params = req.queryParams()
    params.toMap.forEach { (key, value) =>
      paramsMap.put(key, value.toArray(new Array[String](0)))
      ()
    }
    val requestContext = RequestContext
      .Builder
      .create()
      .requestURI(URI.create(req.path().toString)) // excludes context path
      .queryString(req.query())
      .parameterMap(paramsMap)
      .build()
    req.headers().toMap.forEach { (key, value) =>
      requestContext.getHeaders.put(key, value)
      ()
    }

    endpointOpts.defaultHeaders.mappings.foreach {
      case (key, value) =>
        res.headers().add(key, value.asJava)
        requestContext.getHeaders.put(key, value.asJava)
        ()
    }

    // Use Tyrus to process a WebSocket upgrade request
    val upgradeResponse = new TyrusUpgradeResponse()
    val upgradeInfo = engine.upgrade(requestContext, upgradeResponse)

    // Respond to upgrade request using response from Tyrus
    res.status(upgradeResponse.getStatus)
    upgradeResponse.getHeaders.forEach { (key, value) =>
      res.headers().add(key, value)
      ()
    }
    val publisherWriter = new TyrusWriterPublisher()

    // Force BareResponseImpl to websocket mode
    res
      .headers()
      .send()
      .forSingle { _ =>
        res.send(publisherWriter)
        ()
      }

    // Write reason for failure if not successful
    if (upgradeInfo.getStatus != WebSocketEngine.UpgradeStatus.SUCCESS) {
      val reason = upgradeResponse.getReasonPhrase
      if (reason != null) {
        publisherWriter.write(ByteBuffer.wrap(reason.getBytes(UTF_8)), null)
      }
    }

    // Flush upgrade response
    publisherWriter.write(FLUSH_BUFFER, null)

    // Setup the WebSocket connection and subscriber, calls @onOpen
    val connection = upgradeInfo.createConnection(
      publisherWriter,
      closeReason => log.debug("Connection closed: " + closeReason)
    )
    if (connection != null) {
      val subscriber = new WookieeTyrusSubscriber(connection)
      req.content().subscribe(subscriber)
    }
  }
}
