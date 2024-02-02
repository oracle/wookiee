package com.oracle.infy.wookiee.component.web.util

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.web.WebManager
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointOptions
import com.oracle.infy.wookiee.component.web.ws.tyrus.{WookieeTyrusContainer, WookieeTyrusHandler}
import com.oracle.infy.wookiee.test.TestHarness.getFreePort
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import io.helidon.webserver.{Routing, Service, WebServer}
import org.glassfish.tyrus.client.ClientManager
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.ByteBuffer
import javax.websocket.{Endpoint, EndpointConfig, MessageHandler, Session}
import scala.concurrent.{ExecutionContext, Promise}

trait EndpointTestHelper extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val formats: Formats = DefaultFormats
  protected var manager: WebManager = _

  protected lazy val internalPort: Int = getFreePort
  protected lazy val externalPort: Int = getFreePort
  implicit lazy val ec: ExecutionContext = ThreadUtil.createEC(s"web-manager-$internalPort")
  implicit def conf: Config = ConfigFactory.parseString(s"""
       |instance-id = "helidon-test-$internalPort"
       |wookiee-web {
       |  internal-port = $internalPort
       |  external-port = $externalPort
       |    
       |  cors {
       |    internal-allowed-origins = []
       |    external-allowed-origins = [${externalOrigins.map(o => s""""$o"""").mkString(",")}]
       |  }
       |
       |  websocket-keep-alives {
       |    enabled = false
       |    interval = 30s
       |  }
       |}
       |""".stripMargin)

  val clientManager: ClientManager = ClientManager.createClient()

  val testEngine: WookieeTyrusContainer = new WookieeTyrusContainer()
  val testHandler: WookieeTyrusHandler = new WookieeTyrusHandler(testEngine.getWebSocketEngine)
  val testWSPort: Int = getFreePort

  class TestWSRouter extends Service {

    override def update(rules: Routing.Rules): Unit = {
      rules
        .any((req, res) => {
          testHandler.acceptWithContext(req, res, EndpointOptions.default)
        })
      ()
    }
  }

  def externalOrigins: List[String] = List("*")

  override protected def beforeAll(): Unit = {
    new WookieeCommandExecutive("wookiee-command", conf)
    manager = WookieeActor.actorOf(new WebManager("wookiee-web", conf))
    manager.start()
    registerEndpoints(manager)

    val routing = Routing
      .builder()
      .register("/", new TestWSRouter)
      .build()

    val server = WebServer
      .builder()
      .routing(routing)
      .port(testWSPort)
      .build()

    server.start()
    ()
  }

  def registerEndpoints(manager: WebManager): Unit

  def clientEndpoint(promise: Promise[String]): Endpoint = (session: Session, _: EndpointConfig) => {
    session.addMessageHandler(new MessageHandler.Whole[String] {

      override def onMessage(message: String): Unit = {
        promise.success(message)
      }
    })

    session.addMessageHandler(new MessageHandler.Whole[ByteBuffer] {

      override def onMessage(message: ByteBuffer): Unit = {
        promise.success(new String(message.array()))
      }
    })

  }
}
