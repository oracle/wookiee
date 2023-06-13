package com.oracle.infy.wookiee.component.helidon.web.ws

import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{EndpointType, WookieeRequest}
import com.oracle.infy.wookiee.component.helidon.web.ws.tyrus.{WookieeTyrusContainer, WookieeTyrusSubscriber}
import io.helidon.common.http.DataChunk
import org.glassfish.tyrus.ext.extension.deflate.PerMessageDeflateExtension
import org.glassfish.tyrus.spi.{Connection, ReadHandler, Writer}

import java.net.URI
import java.util
import java.util.Collections
import java.util.concurrent.Flow
import javax.websocket._
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class WookieeWebsocketTest extends EndpointTestHelper {
  "Wookiee Websocket" should {
    "return expected message" in {

      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(endpoint, new URI(s"ws://localhost:$internalPort/ws/test?bogus=value=param"))

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(messageFromServer == "Got message: [Hello from client!]")
    }

    "can send along headers" in {

      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      val cec = ClientEndpointConfig
        .Builder
        .create()
        .configurator(new ClientEndpointConfig.Configurator {
          override def beforeRequest(headers: util.Map[String, util.List[String]]): Unit = {
            headers.put("Header-1", util.Arrays.asList("value1"))
            headers.put("Header-2", util.Arrays.asList("value2"))
            ()
          }
        })
        .build()

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(endpoint, cec, new URI(s"ws://localhost:$internalPort/ws/headers"))

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(messageFromServer == "Got message: [Hello from client!], Header-1=value1, Header-2=value2")
    }

    "can handle compression" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      val cec = ClientEndpointConfig
        .Builder
        .create()
        .extensions(Collections.singletonList(new PerMessageDeflateExtension()))
        .build()

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(endpoint, cec, new URI(s"ws://localhost:$internalPort/ws/test"))

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(messageFromServer == "Got message: [Hello from client!]")

    }

    "has support for query parameters and segments" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(endpoint, new URI(s"ws://localhost:$internalPort/ws/value1/value2?param3=value3"))

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(
        messageFromServer == "Got message: [Hello from client!], param1=[value1], param2=[value2], param3=[value3]"
      )

    }

    "container doesn't register directly" in {
      val container = new WookieeTyrusContainer
      intercept[UnsupportedOperationException] {
        container.register(getClass)
      }
      intercept[UnsupportedOperationException] {
        container.register(getClass)
      }
    }

    "subscriber releases items" in {
      val mockConnection: Connection = new Connection {
        override def getReadHandler: ReadHandler = ???
        override def getWriter: Writer = ???
        override def getCloseListener: Connection.CloseListener = ???
        override def close(reason: CloseReason): Unit = {}
      }

      val tyrusSubscriber: WookieeTyrusSubscriber = new WookieeTyrusSubscriber(mockConnection) {
        override protected val MaxRetries: Int = 0
      }

      tyrusSubscriber.onNext(DataChunk.create("test".getBytes))
      tyrusSubscriber.onSubscribe(new Flow.Subscription {
        override def request(n: Long): Unit = {}
        override def cancel(): Unit = {}
      })
      tyrusSubscriber.onNext(DataChunk.create("test".getBytes))
      tyrusSubscriber.onComplete()
    }

  }

  override def registerEndpoints(manager: HelidonManager): Unit = {
    HelidonManager.registerWebsocket(new WookieeWebsocket {
      override def path: String = "/ws/test"

      override def handleText(text: String, request: WookieeRequest)(implicit session: Session): Unit = {
        reply(s"Got message: [$text]")
      }

      override def endpointType: EndpointType = EndpointType.BOTH
    })

    HelidonManager.registerWebsocket(new WookieeWebsocket {
      override def path: String = "/ws/headers"

      override def handleText(text: String, request: WookieeRequest)(implicit session: Session): Unit =
        reply(
          s"Got message: [$text], Header-1=${request.headers.mappings("Header-1").head}, Header-2=${request.headers.mappings("Header-2").head}"
        )

      override def endpointType: EndpointType = EndpointType.BOTH
    })

    HelidonManager.registerWebsocket(new WookieeWebsocket {
      override def path: String = "/ws/$param1/$param2"

      override def handleText(text: String, request: WookieeRequest)(implicit session: Session): Unit =
        reply(
          s"Got message: [$text], param1=[${request.pathSegments("param1")}], " +
            s"param2=[${request.pathSegments("param2")}], param3=[${request.queryParameters("param3")}]"
        )

      override def endpointType: EndpointType = EndpointType.BOTH
    })
  }

}
