package com.oracle.infy.wookiee.component.helidon.util

import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.test.TestHarness.getFreePort
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.glassfish.tyrus.client.ClientManager
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import javax.websocket.{Endpoint, EndpointConfig, MessageHandler, Session}
import scala.concurrent.{ExecutionContext, Promise}

trait EndpointTestHelper extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val formats: Formats = DefaultFormats
  protected var manager: HelidonManager = _

  protected lazy val internalPort: Int = getFreePort
  protected lazy val externalPort: Int = getFreePort
  implicit val ec: ExecutionContext = ThreadUtil.createEC("helidon-manager-test")
  implicit lazy val conf: Config = ConfigFactory.parseString(s"""
       |instance-id = "helidon-test-$internalPort"
       |wookiee-helidon {
       |  web {
       |    internal-port = $internalPort
       |    external-port = $externalPort
       |    
       |    cors {
       |      internal-allowed-origins = []
       |      external-allowed-origins = [${externalOrigins.map(o => s""""$o"""").mkString(",")}]
       |    }
       |  }
       |}
       |""".stripMargin)

  val clientManager: ClientManager = ClientManager.createClient()

  def externalOrigins: List[String] = List("*")

  override protected def beforeAll(): Unit = {
    new WookieeCommandExecutive("wookiee-command", conf)
    manager = new HelidonManager("wookiee-helidon", conf)
    manager.start()
    registerEndpoints(manager)
  }

  def registerEndpoints(manager: HelidonManager): Unit

  def clientEndpoint(promise: Promise[String]): Endpoint = (session: Session, _: EndpointConfig) => {
    session.addMessageHandler(new MessageHandler.Whole[String] {

      override def onMessage(message: String): Unit = {
        promise.success(message)
      }
    })

  }
}
