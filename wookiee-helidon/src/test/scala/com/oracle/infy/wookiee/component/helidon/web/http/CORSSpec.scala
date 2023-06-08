package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.web.client.WookieeWebClient.{getContent, oneOff}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{CorsWhiteList, EndpointOptions, EndpointType}
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.HttpHandler
import io.helidon.webserver.{ServerRequest, ServerResponse}

class CORSSpec extends EndpointTestHelper {

  override def externalOrigins: List[String] = List("http://origin.safe")

  override def registerEndpoints(manager: HelidonManager): Unit = {
    List("GET", "POST", "PATCH", "PUT", "DELETE").foreach { method =>
      manager.registerEndpoint(
        "/api/v1/endpoint",
        EndpointType.INTERNAL,
        method,
        HttpHandler(
          (_: ServerRequest, _: ServerResponse) => (),
          EndpointOptions
            .default
            .copy(
              allowedHeaders = Some(
                CorsWhiteList(List("valid-header-1", "valid-header-2"))
              )
            )
        )
      )
    }

    manager.registerEndpoint(
      "/api/v1/endpoint",
      EndpointType.EXTERNAL,
      "GET",
      HttpHandler(
        (_: ServerRequest, _: ServerResponse) => (),
        EndpointOptions
          .default
          .copy(
            allowedHeaders = Some(
              CorsWhiteList(List("valid-header-1", "valid-header-2"))
            )
          )
      )
    )
  }

  "Wookiee Helidon endpoint CORS support" should {
    "handle basic pre-flight requests and provide methods" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "/api/v1/endpoint",
          "OPTIONS",
          """{"key":"value"}""",
          Map()
        )

      val methods = List("GET", "POST", "PATCH", "PUT", "DELETE")
      val supportedMethods = response.headers().value("Access-Control-Allow-Methods").get().split(",")
      supportedMethods must contain theSameElementsAs methods
    }

    "return access control headers in pre-flight" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "/api/v1/endpoint",
          "OPTIONS",
          """{"key":"value"}""",
          Map("Access-Control-Request-Headers" -> "valid-header-1,valid-header-2,invalid-header-1")
        )

      val headers = List("valid-header-1", "valid-header-2")
      val supportedHeaders = response.headers().value("Access-Control-Allow-Headers").get().split(",")
      supportedHeaders must contain theSameElementsAs headers
      response.headers().value("Access-Control-Allow-Origin").get() mustEqual "*"
    }

    "return origin headers in pre-flight" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "/api/v1/endpoint",
          "OPTIONS",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe")
        )

      response.headers().value("Access-Control-Allow-Origin").get() mustEqual "http://origin.safe"
      response.headers().value("Access-Control-Allow-Credentials").get() mustEqual "true"
    }

    "reject invalid origins" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "/api/v1/endpoint",
          "OPTIONS",
          """{"key":"value"}""",
          Map("Origin" -> "http://bad.news")
        )

      response.status().code() mustEqual 403
      getContent(response) mustEqual "Origin not permitted."
    }

    "return origin info on normal requests" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "/api/v1/endpoint",
          "OPTIONS",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe")
        )

      response.headers().value("Access-Control-Allow-Origin").get() mustEqual "http://origin.safe"
      response.headers().value("Access-Control-Allow-Credentials").get() mustEqual "true"
    }
  }
}
