package com.oracle.infy.wookiee.component.helidon.web.ws

import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.web.WookieeEndpoints
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{EndpointType, WookieeRequest}
import com.oracle.infy.wookiee.component.helidon.web.ws.tyrus.{WookieeTyrusContainer, WookieeTyrusSubscriber}
import com.oracle.infy.wookiee.utils.ThreadUtil
import io.helidon.common.http.DataChunk
import org.glassfish.tyrus.ext.extension.deflate.PerMessageDeflateExtension
import org.glassfish.tyrus.spi.{Connection, ReadHandler, Writer}

import java.net.URI
import java.nio.ByteBuffer
import java.util
import java.util.Collections
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import javax.websocket._
import javax.websocket.server.ServerEndpointConfig
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.jdk.CollectionConverters._

class WookieeWebsocketSpec extends EndpointTestHelper {
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

      val cec = confWithHeaders(Map("Header-1" -> List("value1"), "Header-2" -> List("value2")))

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

    "handle functional endpoint registration" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      val cec = confWithHeaders(Map("Authorization" -> List("token")))

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(
          endpoint,
          cec,
          new URI(s"ws://localhost:$internalPort/ws/functional/value1?param=value2")
        )

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(
        messageFromServer == "Got message: [Hello from client!], auth=[token], path=[value1], query=[value2]"
      )
    }

    "hits the defined WS handlers" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(
          endpoint,
          new URI(s"ws://localhost:$internalPort/ws/functional/advanced")
        )

      session.getBasicRemote.sendText("error")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(
        messageFromServer == "Got error: [error on purpose]"
      )
      ThreadUtil.awaitResult({ if (onCloseCalled.get()) Some(true) else None }) mustBe true
    }

    "fails gracefully on functional" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      var cec = confWithHeaders(Map("Authorization" -> List("fail")))

      // Connect to the server endpoint
      var session =
        clientManager.connectToServer(
          endpoint,
          cec,
          new URI(s"ws://localhost:$internalPort/ws/functional/value1?param=value2")
        )

      ThreadUtil.awaitResult({
        if (onCloseFunc.get()) Some(true) else None
      }) mustBe true
      onCloseFunc.set(false)

      cec = confWithHeaders(Map("Authorization" -> List("inner-fail")))

      // Connect to the server endpoint
      session = clientManager.connectToServer(
        endpoint,
        cec,
        new URI(s"ws://localhost:$internalPort/ws/functional/value1?param=value2")
      )

      ThreadUtil.awaitResult({
        if (onCloseFunc.get()) Some(true) else None
      }) mustBe true
    }

    "fails gracefully on object oriented" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      // Connect to the server endpoint
      var session =
        clientManager.connectToServer(
          endpoint,
          new URI(s"ws://localhost:$internalPort/ws/test")
        )
      session.getBasicRemote.sendText("fail")

      // Session should be closed
      ThreadUtil.awaitResult({ if (!session.isOpen) Some(true) else None }) mustBe true

      // Connect to the server endpoint
      session = clientManager.connectToServer(
        endpoint,
        new URI(s"ws://localhost:$internalPort/ws/test")
      )
      session.getBasicRemote.sendText("other-fail")

      ThreadUtil.awaitResult({ if (!session.isOpen) Some(true) else None }) mustBe true
    }

    "provide a usable interface" in {
      val promise = Promise[String]()

      // Define the WebSocket client endpoint
      val endpoint = clientEndpoint(promise)

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(
          endpoint,
          new URI(s"ws://localhost:$internalPort/ws/test/interface?param=value")
        )

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)

      assert(
        messageFromServer == "Got message: [Hello from client!], param = [test], query = [value]"
      )
      ThreadUtil.awaitResult({ if (!session.isOpen) Some(true) else None }) mustBe true
    }

    "has working tyrus implementors" in {
      val cont = new WookieeTyrusContainer
      intercept[UnsupportedOperationException] {
        cont.register(
          ServerEndpointConfig
            .Builder
            .create(getClass, "/")
            .build()
        )
      }
    }
  }

  private val onCloseCalled: AtomicBoolean = new AtomicBoolean(false)
  private val onCloseFunc: AtomicBoolean = new AtomicBoolean(false)

  private def confWithHeaders(addHeaders: Map[String, List[String]]): ClientEndpointConfig = {
    ClientEndpointConfig
      .Builder
      .create()
      .configurator(new ClientEndpointConfig.Configurator {
        override def beforeRequest(headers: util.Map[String, util.List[String]]): Unit = {
          // Put all the headers into the map
          addHeaders.foreach(h => headers.put(h._1, h._2.asJava))
        }
      })
      .build()
  }

  override def registerEndpoints(manager: HelidonManager): Unit = {
    WookieeEndpoints.registerWebsocket(new WookieeWebsocket[Any] {
      override def path: String = "/ws/test"

      override def handleText(text: String, request: WookieeRequest, authInfo: Option[Any])(
          implicit session: Session
      ): Unit = {
        if (text.equals("fail"))
          throw new Exception("error on purpose")
        else if (text.equals("other-fail"))
          throw new IllegalStateException("error on purpose")
        else
          reply(s"Got message: [$text]")
      }

      override def handleError(request: WookieeRequest, authInfo: Option[Any])(
          implicit session: Session
      ): Throwable => Unit = {
        case e: IllegalStateException =>
          super.handleError(request, authInfo)(session)(e)
          reply(s"Got error: [${e.getMessage}]")
          close(Some(("error", 1000)))
        case e: Exception =>
          reply(s"Got error: [${e.getMessage}]")
          close()
      }

      override def endpointType: EndpointType = EndpointType.BOTH
    })

    WookieeEndpoints.registerWebsocket(new WookieeWebsocket[Any] {
      override def path: String = "/ws/headers"

      override def handleText(text: String, request: WookieeRequest, authInfo: Option[Any])(
          implicit session: Session
      ): Unit =
        reply(
          s"Got message: [$text], Header-1=${request.headers.mappings("Header-1").head}, Header-2=${request.headers.mappings("Header-2").head}"
        )

      override def endpointType: EndpointType = EndpointType.BOTH
    })

    WookieeEndpoints.registerWebsocket(new WookieeWebsocket[Any] {
      override def path: String = "/ws/$param1/$param2"

      override def handleText(text: String, request: WookieeRequest, authInfo: Option[Any])(
          implicit session: Session
      ): Unit =
        reply(
          s"Got message: [$text], param1=[${request.pathSegments("param1")}], " +
            s"param2=[${request.pathSegments("param2")}], param3=[${request.queryParameters("param3")}]"
        )

      override def endpointType: EndpointType = EndpointType.BOTH
    })

    case class AuthHolder(auth: String)
    WookieeEndpoints.registerWebsocket[AuthHolder](
      "/ws/functional/$segment",
      EndpointType.BOTH,
      (text: String, interface: WebsocketInterface, authInfo: Option[AuthHolder]) =>
        interface.reply(
          s"Got message: [$text], auth=[${authInfo.map(_.auth).getOrElse("")}], " +
            s"path=[${interface.request.pathSegments("segment")}], " +
            s"query=[${interface.request.queryParameters.getOrElse("param", "")}]"
        ),
      (request: WookieeRequest) => {
        val authHeader = request.headers.mappings.get("Authorization").flatMap(_.headOption.map(AuthHolder.apply))
        authHeader match {
          case Some(AuthHolder("fail"))       => Future.failed(new IllegalStateException("fail on purpose"))
          case Some(AuthHolder("inner-fail")) => throw new IllegalStateException("inner-fail on purpose")
          case Some(auth)                     => Future.successful(Some(auth))
          case None                           => Future.failed(new IllegalStateException("No auth header"))
        }
      },
      onCloseHandler = (_: Option[AuthHolder]) => onCloseFunc.set(true)
    )

    WookieeEndpoints.registerWebsocket[AuthHolder](
      "/ws/functional/advanced",
      EndpointType.BOTH,
      (text: String, interface: WebsocketInterface, _: Option[AuthHolder]) =>
        if (text == "error")
          throw new IllegalStateException("error on purpose")
        else
          interface.reply(
            s"Got message: [$text]"
          ),
      onCloseHandler = (_: Option[AuthHolder]) => onCloseCalled.set(true),
      wsErrorHandler = (interface: WebsocketInterface, _: Option[AuthHolder]) => {
        t: Throwable =>
          interface.reply(
            s"Got error: [${t.getMessage}]"
          )
      }
    )

    WookieeEndpoints.registerWebsocket[AuthHolder](
      "/ws/$segment/interface",
      EndpointType.BOTH,
      (text: String, interface: WebsocketInterface, _: Option[AuthHolder]) => {
        val queryParam = interface.getQueryParams("param")
        val pathParam = interface.getPathSegments("segment")
        interface.reply(
          ByteBuffer.wrap(s"Got message: [$text], param = [$pathParam], query = [$queryParam]".getBytes)
        )
        interface.close()
      }
    )
  }

}
