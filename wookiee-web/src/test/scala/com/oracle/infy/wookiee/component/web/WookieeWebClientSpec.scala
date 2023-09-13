package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.component.web.ProxyServer.WebClientMock
import com.oracle.infy.wookiee.component.web.WebManager.WookieeWebException
import com.oracle.infy.wookiee.component.web.client.WookieeWebClient._
import com.oracle.infy.wookiee.component.web.client.{WebClientLike, WookieeWebClient}
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects.{Content, EndpointType, WookieeResponse}
import com.oracle.infy.wookiee.component.web.http.{HttpCommand, HttpObjects}
import com.oracle.infy.wookiee.component.web.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.web.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.test.TestHarness
import com.oracle.infy.wookiee.utils.ThreadUtil
import io.helidon.webserver.{Handler, Routing, WebServer}
import org.json4s.jackson.Serialization

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class WookieeWebClientSpec extends EndpointTestHelper {
  case class TestObject(content: String)
  val proxyPort: Int = TestHarness.getFreePort
  val proxyServer: WebServer = ProxyServer.startServer(proxyPort, internalPort)
  val requestsSeen: AtomicInteger = new AtomicInteger(0)

  "Wookiee Web Client" should {
    lazy val webClient = WookieeWebClient(s"http://localhost:$internalPort")

    "handle a one-off" in {
      val jsonContent = """{"content":"test"}"""
      val response = oneOff(s"http://localhost:$internalPort", "GET", "/basic/endpoint", "test")
      response.contentString() mustEqual jsonContent
      Serialization.write(response.contentJson()) mustEqual jsonContent
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a basic request in the client" in {
      val response = Await.result(webClient.request("GET", "/basic/endpoint"), 5.seconds)
      response.contentString() mustEqual """{"content":""}"""
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a basic byte request in the client" in {
      val response = Await.result(
        WookieeWebClient.request(webClient.getClient, "GET", "/basic/endpoint", "test".getBytes),
        5.seconds
      )
      response.contentString() mustEqual """{"content":"test"}"""
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a basic object request in the client" in {
      val response =
        Await.result(WookieeWebClient.request(webClient.helidonClient, "GET", "/basic/endpoint", "test"), 5.seconds)
      response.contentString() mustEqual """{"content":"test"}"""
      response.code() mustEqual 200
      response.headerMap()("Content-Type").head mustEqual "application/json"
    }

    "handle a proxy request in the client" in {
      val webClient = WookieeWebClient(s"http://localhost:$internalPort", Some(ProxyConfig("localhost", proxyPort)))
      val currentCount = requestsSeen.get()
      val response = Await.result(webClient.request("GET", "/basic/endpoint"), 5.seconds)
      response.code() mustEqual 404
      response.headerMap()("Content-Type").head mustEqual "text/plain"
      ThreadUtil.awaitEvent({ requestsSeen.get() > currentCount })
    }

    "gracefully fail outside of future" in {
      val webClient = WookieeWebClient(s"http://localhost:$internalPort", Some(ProxyConfig("localhost", proxyPort)))
      intercept[NullPointerException] {
        Await.result(
          WookieeWebClient.requestAndRespond(webClient.helidonClient, "GET", "/basic/endpoint", Array(), null, null),
          5.seconds
        )
      }
    }

    "allow extension of client like" in {
      val client = new WebClientMock
      val response = Await.result(client.request("GET", "/basic/endpoint"), 5.seconds)
      response.contentString() mustEqual "test"
    }

    "handle an extract via the object" in {
      var response =
        Await.result(
          WookieeWebClient.requestAndExtract[TestObject](webClient.helidonClient, "GET", "/basic/endpoint", "test"),
          5.seconds
        )
      response.content mustEqual "test"
      response = Await.result(webClient.requestAndExtract[TestObject]("GET", "/basic/endpoint"), 5.seconds)
      response.content mustEqual ""
    }

    "throw an error when code is not 200-299" in {
      intercept[WookieeWebException] {
        Await.result(webClient.requestAndExtract("GET", "/basic/command", "fail"), 5.seconds)
      }
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    proxyServer.shutdown()
    ()
  }

  override def registerEndpoints(manager: WebManager): Unit = {
    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "basic-endpoint",
      "/basic/endpoint",
      "GET",
      EndpointType.INTERNAL, { request =>
        if (request.contentString().equals("fail")) Future.failed(new Exception("intentional-fail"))
        else Future.successful(InputObject(s"""{"content":"${request.content.asString}"}"""))
      }, { input =>
        requestsSeen.incrementAndGet()
        Future.successful(OutputObject(input.value))
      }, { output =>
        WookieeResponse(Content(output.value))
      }, { throwable =>
        WookieeResponse(Content(throwable.getMessage))
      }
    )

    class BasicGet extends HttpCommand {
      override def commandName: String = "basic-get-command"

      override def method: String = "get"

      override def path: String = "basic/command"

      override def endpointType: EndpointType = EndpointType.INTERNAL

      override def execute(input: HttpObjects.WookieeRequest): Future[WookieeResponse] = Future.successful {
        if (input.content.asString.equals("fail")) throw WookieeWebException("fail=error", None, Some(500))
        else WookieeResponse(input.content)
      }
    }

    WookieeEndpoints.registerEndpoint(new BasicGet)
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
        method: String,
        path: String,
        content: String,
        queryParams: Map[String, String],
        headers: Map[String, String],
        log: Boolean,
        contentType: String
    ): Future[WookieeResponse] =
      Future.successful(WookieeResponse(Content("test")))
  }

  def handleProxy(
      targetPort: Int
  ): Handler = { (request, response) =>
    val reqContent = request.content().as(classOf[String]).await()
    val targetResponse =
      oneOff(s"http://localhost:$targetPort", "GET", "/basic/endpoint", reqContent)
    response.addHeader("Content-Type", "application/json")
    response.status(targetResponse.code()).send()
    ()
  }
}
