package com.oracle.infy.wookiee.component.helidon.web

import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.component.helidon.web.ProxyServer.WebClientMock
import com.oracle.infy.wookiee.component.helidon.web.client.WookieeWebClient._
import com.oracle.infy.wookiee.component.helidon.web.client.{WebClientLike, WookieeWebClient}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{Content, EndpointType, WookieeResponse}
import com.oracle.infy.wookiee.test.TestHarness
import com.oracle.infy.wookiee.utils.ThreadUtil
import io.helidon.webserver.{Handler, Routing, WebServer}
import org.json4s.jackson.Serialization

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

class WookieeWebClientSpec extends EndpointTestHelper {
  val proxyPort: Int = TestHarness.getFreePort
  val proxyServer: WebServer = ProxyServer.startServer(proxyPort, internalPort)
  val requestsSeen: AtomicInteger = new AtomicInteger(0)

  "Wookiee Web Client" should {
    lazy val webClient = WookieeWebClient(s"http://localhost:$internalPort")

    "handle a one-off" in {
      val jsonContent = """{"content":"test"}"""
      val response = oneOff(s"http://localhost:$internalPort", "/basic/endpoint", "GET", "test")
      response.contentString() mustEqual jsonContent
      Serialization.write(response.contentJson()) mustEqual jsonContent
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a basic request in the client" in {
      val response = webClient.request("/basic/endpoint", "GET")
      response.contentString() mustEqual """{"content":""}"""
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a basic object request in the client" in {
      val response = WookieeWebClient.request(webClient.helidonClient, "/basic/endpoint", "GET", "test")
      response.contentString() mustEqual """{"content":"test"}"""
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a proxy request in the client" in {
      val webClient = WookieeWebClient(s"http://localhost:$internalPort", Some(ProxyConfig("localhost", proxyPort)))
      val currentCount = requestsSeen.get()
      val response = webClient.request("/basic/endpoint", "GET")
      response.code() mustEqual 404
      response.headerMap()("Content-Type").head mustEqual "text/plain"
      ThreadUtil.awaitEvent({ requestsSeen.get() > currentCount })
    }

    "allow extension of client like" in {
      val client = new WebClientMock
      val response = client.request("/basic/endpoint", "GET")
      response.contentString() mustEqual "test"
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    proxyServer.shutdown()
    ()
  }

  override def registerEndpoints(manager: HelidonManager): Unit = {
    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "basic-endpoint",
      "/basic/endpoint",
      "GET",
      EndpointType.INTERNAL, { request =>
        Future.successful(InputObject(s"""{"content":"${request.content.asString}"}"""))
      }, { input =>
        requestsSeen.incrementAndGet()
        Future.successful(OutputObject(input.value))
      }, { output =>
        WookieeResponse(Content(output.value))
      }, { throwable =>
        WookieeResponse(Content(throwable.getMessage))
      }
    )
  }
}

object ProxyServer {

  def startServer(hostPort: Int, targetPort: Int): WebServer = {
    val routing: Routing = Routing
      .builder()
      .any("/", handleProxy(targetPort))
      .build()

    val server = WebServer
      .builder()
      .routing(routing)
      .port(hostPort)
      .build()

    server
      .start()
      .thenAccept(ws => println(s"Server started at: http://localhost:${ws.port}"))
    server
  }

  class WebClientMock extends WebClientLike {

    override def request(
        path: String,
        method: String,
        content: String,
        queryParams: Map[String, String],
        headers: Map[String, String],
        log: Boolean,
        contentType: String
    ): WookieeResponse =
      WookieeResponse(Content("test"))
  }

  def handleProxy(
      targetPort: Int
  ): Handler = { (request, response) =>
    val reqContent = request.content().as(classOf[String]).await()
    val targetResponse =
      oneOff(s"http://localhost:$targetPort", "/basic/endpoint", "GET", reqContent)
    response.addHeader("Content-Type", "application/json")
    response.status(targetResponse.code()).send()
    ()
  }
}
