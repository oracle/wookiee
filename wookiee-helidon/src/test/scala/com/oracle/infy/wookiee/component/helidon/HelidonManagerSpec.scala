package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.component.helidon.web.WookieeEndpoints
import com.oracle.infy.wookiee.component.helidon.web.client.WookieeWebClient._
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.HttpHandler
import com.oracle.infy.wookiee.component.helidon.web.http.{HttpCommand, HttpObjects}
import io.helidon.webclient.WebClient
import org.json4s.jackson.JsonMethods._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

class HelidonManagerSpec extends EndpointTestHelper {
  private val directiveHit: AtomicBoolean = new AtomicBoolean(false)

  override def registerEndpoints(manager: HelidonManager): Unit = {
    manager.registerEndpoint(
      "/api/$variable/endpoint",
      EndpointType.BOTH,
      "GET",
      HttpHandler((req, res) => {
        req
          .content()
          .as(classOf[String])
          .thenAccept(jsonString => {
            val json = parse(jsonString)
            res.headers().add("content-type", "application/json")
            res.send(compact(render(json)))
            ()
          })
        ()
      })
    )

    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "account-endpoint",
      "account/$accountId",
      "GET",
      EndpointType.INTERNAL, { request =>
        Future.successful(InputObject(request.pathSegments("accountId")))
      }, { input =>
        Future.successful(OutputObject(input.value))
      }, { output =>
        WookieeResponse(Content(output.value))
      }, { throwable =>
        WookieeResponse(Content(throwable.getMessage))
      }
    )

    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "basic-endpoint",
      "/basic/endpoint",
      "POST",
      EndpointType.INTERNAL, { request =>
        if (request.content.asString.equals("parse-fail"))
          Future.failed(new Exception("fail=parse"))
        else if (request.content.asString.equals("error-fail"))
          Future.failed(new Exception("fail=error"))
        else Future.successful(InputObject(request.content.asString))
      }, { input =>
        if (input.value.equals("business-fail")) Future.failed(new Exception("fail=business"))
        else Future.successful(OutputObject(input.value))
      }, { output =>
        if (output.value.equals("output-fail")) throw new Exception("fail=output")
        else WookieeResponse(Content(output.value))
      }, { throwable =>
        if (throwable.getMessage.equals("fail=error")) throw new Exception("fail=error")
        else WookieeResponse(Content(throwable.getMessage))
      },
      endpointOptions = EndpointOptions(
        routeTimerLabel = Some("route-timer"),
        requestHandlerTimerLabel = Some("request-handler-timer"),
        businessLogicTimerLabel = Some("business-logic-timer"),
        responseHandlerTimerLabel = Some("response-handler-timer")
      )
    )

    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "segment-endpoint",
      "/api/$segment/endpoint",
      "POST",
      EndpointType.EXTERNAL, { request =>
        val segment = request.pathSegments.getOrElse("segment", "")
        val query = request.queryParameters.getOrElse("query", "")
        val header = request.headers.mappings.getOrElse("header", List()).mkString(",")
        Future.successful(
          InputObject(
            s"""{"segment":"$segment","query":"$query","header":"$header"}"""
          )
        )
      }, { input =>
        Future.successful(OutputObject(input.value))
      }, { output =>
        WookieeResponse(Content(output.value))
      }, { throwable =>
        WookieeResponse(Content(throwable.getMessage))
      }
    )

    class BasicGet extends HttpCommand {
      override def commandName: String = "basic-get-command"

      override def endpointOptions: EndpointOptions =
        super.endpointOptions.copy(defaultHeaders = HttpObjects.Headers(Map("header" -> List("value"))))

      override def method: String = "get"

      override def path: String = "basic/command"

      override def endpointType: EndpointType = EndpointType.INTERNAL

      override def requestDirective(request: WookieeRequest): WookieeRequest = {
        directiveHit.set(true)
        request
      }

      override def execute(input: HttpObjects.WookieeRequest): Future[WookieeResponse] = Future.successful {
        if (input.content.asString.equals("fail")) throw new Exception("fail=error")
        else WookieeResponse(input.content)
      }
    }

    WookieeEndpoints.registerEndpoint(new BasicGet)

    class ErrorBomb extends HttpCommand {
      override def method: String = "get"

      override def path: String = throw new Exception("bomb")

      override def endpointType: EndpointType = EndpointType.INTERNAL

      override def execute(input: HttpObjects.WookieeRequest): Future[WookieeResponse] = Future.successful {
        WookieeResponse(input.content)
      }
    }

    manager.registerEndpoint(
      "/error/bomb",
      EndpointType.BOTH,
      "GET",
      WookieeRouter.handlerFromCommand(new ErrorBomb)
    )

  }

  override protected def afterAll(): Unit =
    manager.prepareForShutdown()

  "Helidon Manager" should {
    val jsonPayload = """{"key":"value"}"""

    "handle a call to the '/api/test/endpoint' endpoint" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/api/test/endpoint",
          jsonPayload,
          Map()
        )
      )

      responseContent mustBe jsonPayload
    }

    "support for hosting on both external and internal" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$externalPort",
          "GET",
          "/api/test/endpoint",
          jsonPayload,
          Map()
        )
      )

      responseContent mustBe jsonPayload
    }

    "allow registration of basic endpoints" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "POST",
          "/basic/endpoint",
          jsonPayload,
          Map()
        )
      )

      responseContent mustBe jsonPayload
    }

    "extract path params properly" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/account/50024",
          jsonPayload,
          Map()
        )
      )

      responseContent mustBe "50024"
    }

    "handle failures with error handler" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "POST",
          "/basic/endpoint",
          "parse-fail",
          Map()
        )
      )

      responseContent mustBe "fail=parse"

      val responseContent2 = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "POST",
          "/basic/endpoint",
          "business-fail",
          Map()
        )
      )

      responseContent2 mustBe "fail=business"

      val responseContent3 = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "POST",
          "/basic/endpoint",
          "output-fail",
          Map()
        )
      )

      responseContent3 mustBe "fail=output"

      val content = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "POST",
          "/basic/endpoint",
          "error-fail",
          Map()
        )
      )

      content mustEqual "There was an internal server error."
    }

    "support segments, query parameters, and headers" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$externalPort",
          "POST",
          "/api/segment/endpoint?query=param",
          jsonPayload,
          Map("header" -> "value")
        )
      )

      responseContent mustBe """{"segment":"segment","query":"param","header":"value"}"""
    }

    "check client can parse all method types" in {
      val methods = List("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "OTHER")
      val client = WebClient
        .builder()
        .baseUri(s"http://localhost:$externalPort")
        .build()

      methods.foreach { method =>
        methodRequested(client, method)
        "passed" mustEqual "passed"
      }
    }

    "can handle registering a route using a command" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/basic/command",
          jsonPayload,
          Map()
        )
      )

      directiveHit.get() mustBe true
      responseContent mustBe jsonPayload
    }

    "can fall into default error handling" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/basic/command",
          "fail",
          Map()
        )
      )

      responseContent mustBe "There was an internal server error."
    }

    "gets coverage on case class objects" in {
      val req = WookieeRequest(Content("test"), Map(), Map(), HttpObjects.Headers(Map()))
      req.contentString() mustEqual "test"
      req.headerMap() mustEqual Map()
      WookieeRequest.unapply(req) must not be None

      val cors = AllowSome(List())
      AllowSome.unapply(cors) must not be None

      val cors2 = AllowAll()
      cors2.allowed(List("anything")) mustEqual List("anything")
      AllowAll.unapply(cors2) must not be None

      val eo = EndpointOptions()
      EndpointOptions.unapply(eo) must not be None

      val con = Content("test")
      Content.unapply(con) must not be None

      val pConf = ProxyConfig("test", 1234)
      ProxyConfig.unapply(pConf) must not be None
    }

    "hit error handling in handler for commands" in {
      val responseContent = getContent(
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/error/bomb",
          "fail",
          Map()
        )
      )

      responseContent mustBe "There was an internal server error."
    }

    "return a 404 properly" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/does/not/exist",
          "fail",
          Map()
        )

      getContent(response) mustBe "Endpoint not found."
      response.code() mustEqual 404
    }
  }
}
