package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.component.helidon.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.component.helidon.web.client.WookieeWebClient
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{Content, EndpointType, WookieeResponse}
import com.oracle.infy.wookiee.test.TestHarness.getFreePort
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletionException
import scala.concurrent.{ExecutionContext, Future}

class HelidonManagerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val formats: Formats = DefaultFormats
  private var manager: HelidonManager = _

  private lazy val internalPort = getFreePort
  private lazy val externalPort = getFreePort
  implicit val ec: ExecutionContext = ThreadUtil.createEC("helidon-manager-test")
  implicit lazy val conf: Config = ConfigFactory.parseString(s"""
       |instance-id = "helidon-test-$internalPort"
       |wookiee-helidon {
       |  web {
       |    internal-port = $internalPort
       |    external-port = $externalPort
       |  }
       |}
       |""".stripMargin)

  override protected def beforeAll(): Unit = {
    manager = new HelidonManager("wookiee-helidon", conf)
    manager.start()

    manager.registerEndpoint(
      "/api/$variable/endpoint",
      EndpointType.BOTH,
      "GET",
      (req, res) => {
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
      }
    )

    HelidonManager.registerEndpoint[InputObject, OutputObject](
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
      }
    )

    HelidonManager.registerEndpoint[InputObject, OutputObject](
      "segment-endpoint",
      "/api/$segment/endpoint",
      "POST",
      EndpointType.EXTERNAL, { request =>
        val segment = request.pathSegments.getOrElse("segment", "")
        val query = request.queryParameters.getOrElse("query", List()).mkString(",")
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
  }

  override protected def afterAll(): Unit =
    manager.prepareForShutdown()

  "Helidon Manager" should {
    val jsonPayload = """{"key":"value"}"""

    "handle a call to the '/api/test/endpoint' endpoint" in {
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/api/test/endpoint",
        "GET",
        jsonPayload,
        Map()
      )

      responseContent mustBe jsonPayload
    }

    "support for hosting on both external and internal" in {
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$externalPort",
        "/api/test/endpoint",
        "GET",
        jsonPayload,
        Map()
      )

      responseContent mustBe jsonPayload
    }

    "allow registration of basic endpoints" in {
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/basic/endpoint",
        "POST",
        jsonPayload,
        Map()
      )

      responseContent mustBe jsonPayload
    }

    "handle failures with error handler" in {
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/basic/endpoint",
        "POST",
        "parse-fail",
        Map()
      )

      responseContent mustBe "fail=parse"

      val responseContent2 = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/basic/endpoint",
        "POST",
        "business-fail",
        Map()
      )

      responseContent2 mustBe "fail=business"

      val responseContent3 = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/basic/endpoint",
        "POST",
        "output-fail",
        Map()
      )

      responseContent3 mustBe "fail=output"

      intercept[CompletionException] {
        WookieeWebClient.oneOff(
          s"http://localhost:$internalPort",
          "/basic/endpoint",
          "POST",
          "error-fail",
          Map()
        )
      }
    }

    "support segments, query parameters, and headers" in {
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$externalPort",
        "/api/segment/endpoint?query=param",
        "POST",
        jsonPayload,
        Map("header" -> "value")
      )

      responseContent mustBe """{"segment":"segment","query":"param","header":"value"}"""
    }
  }
}
