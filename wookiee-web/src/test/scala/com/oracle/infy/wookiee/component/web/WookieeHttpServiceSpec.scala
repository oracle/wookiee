package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.component.web.client.WookieeWebClient.{getContent, oneOff}
import com.oracle.infy.wookiee.component.web.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.{ComponentInfoV2, ComponentManager, WookieeComponent}
import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.test.BaseWookieeTest
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TestHttpService(config: Config) extends WookieeHttpService(config) {

  override def addCommands(implicit conf: Config, ec: ExecutionContext): Unit =
    WookieeHttpServiceSpec.calledServiceCommands.set(true)
}

class TestHttpComponent(name: String, config: Config) extends WookieeHttpComponent(name, config) {

  override def addCommands(implicit conf: Config, ec: ExecutionContext): Unit =
    WookieeHttpServiceSpec.calledComponentCommands.set(true)
}

object WookieeHttpServiceSpec {
  val calledServiceCommands: AtomicReference[Boolean] = new AtomicReference(false)
  val calledComponentCommands: AtomicReference[Boolean] = new AtomicReference(false)
}

class WookieeHttpServiceSpec extends AnyWordSpec with Matchers with BaseWookieeTest with EndpointTestHelper {
  override implicit lazy val ec: ExecutionContext = ThreadUtil.createEC(s"wookiee-test-${testWookiee.getInstanceId}")

  override def startupWait: FiniteDuration = 40.seconds

  def simpleGet(path: String): String = {
    getContent(
      oneOff(
        s"http://localhost:$internalPort",
        "GET",
        path,
        "",
        Map()
      )
    )
  }

  "Wookiee HTTP Service trait" should {
    "kick off addCommands when wookiee-web is ready in service" in {
      WookieeHttpServiceSpec.calledServiceCommands.get() mustEqual true
    }

    "kick off addCommands when wookiee-web is ready in component" in {
      WookieeHttpServiceSpec.calledComponentCommands.get() mustEqual true
    }

    "host the metrics endpoint" in {
      val responseContent = simpleGet("/metrics")
      responseContent.contains("metrics") mustEqual true
    }

    "host the healthcheck endpoint" in {
      var response: String = ""
      response = simpleGet("/healthcheck")
      response.contains("NORMAL") mustEqual true
      response = simpleGet("/healthcheck/full")
      response.contains("NORMAL") mustEqual true
      response = simpleGet("/healthcheck/lb")
      response mustEqual "\"UP\""
      response = simpleGet("/healthcheck/nagios")
      response mustEqual "\"NORMAL|Thunderbirds are GO\""
    }

    "host all other default endpoints" in {
      var response = simpleGet("favicon.ico")
      response mustEqual ""
      response = simpleGet("ping")
      response.contains("pong") mustEqual true
    }
  }

  override protected def beforeAll(): Unit = {
    manager = ComponentManager
      .getComponentByName("wookiee-web")
      .get
      .asInstanceOf[ComponentInfoV2]
      .component
      .asInstanceOf[WebManager]
    registerEndpoints(manager)
  }

  override def servicesMap: Option[Map[String, Class[_ <: WookieeService]]] =
    Some(Map("TestHttpService" -> classOf[TestHttpService]))

  override def componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] =
    Some(Map("TestHttpComponent" -> classOf[TestHttpComponent]))

  override def registerEndpoints(manager: WebManager): Unit = {}

  override def config: Config = conf
}
