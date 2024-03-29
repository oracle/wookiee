package com.oracle.infy.wookiee.component.web.http

import com.oracle.infy.wookiee.component.web.client.WookieeWebClient.oneOff
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter.HttpHandler
import com.oracle.infy.wookiee.component.web.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.web.ws.WookieeWebsocket
import com.oracle.infy.wookiee.component.web.{WebManager, WookieeEndpoints}
import io.helidon.webserver.{ServerRequest, ServerResponse}

import java.net.URI
import java.util
import java.util.concurrent.TimeUnit
import javax.websocket.{ClientEndpointConfig, DeploymentException, Session}
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class CORSSpec extends EndpointTestHelper {

  override def externalOrigins: List[String] = List("http://origin.safe", "http://other.origin")

  override def registerEndpoints(manager: WebManager): Unit = {
    List("GET", "POST", "PATCH", "PUT", "DELETE").foreach { method =>
      manager.registerEndpoint(
        "/api/v1/endpoint",
        EndpointType.INTERNAL,
        method,
        HttpHandler(
          (_: ServerRequest, res: ServerResponse) => {
            res.status(200).send("OK")
            ()
          },
          EndpointOptions
            .default
            .copy(
              allowedHeaders = Some(
                CorsWhiteList(List("valid-header-1", "valid-header-2"))
              ),
              allowedOrigins = Some(
                CorsWhiteList(List("http://origin.safe", s"http://other.origin.$method", "http://bad.origin"))
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
        (_: ServerRequest, res: ServerResponse) => {
          res.status(200).send("OK")
          ()
        },
        EndpointOptions()
          .copy(
            allowedHeaders = Some(
              CorsWhiteList(List("valid-header-1", "valid-header-2"))
            ),
            allowedOrigins = Some(
              CorsWhiteList(List("http://origin.safe", "http://bad.origin"))
            )
          )
      )
    )

    WookieeEndpoints.registerWebsocket(new WookieeWebsocket[Any] {
      override def path: String = "/ws/cors"

      override def handleText(text: String, request: WookieeRequest, authInfo: Option[Any])(
          implicit session: Session
      ): Unit =
        reply(s"Got message: [$text],Default-Header: [${request.headers.getStringValue("Default-Header")}]")

      override def endpointType: EndpointType = EndpointType.BOTH

      override def endpointOptions: EndpointOptions =
        EndpointOptions
          .default
          .copy(
            defaultHeaders = Headers(Map("Default-Header" -> List("default"))),
            allowedHeaders = Some(CorsWhiteList(List("Header-1", "Header-2")))
          )
      override def wsKeepAlive: Boolean = false
      override def wsKeepAliveDuration: FiniteDuration = FiniteDuration.apply(30, TimeUnit.SECONDS)
    })
  }

  "Wookiee Helidon endpoint CORS support" should {
    "handle basic pre-flight requests and provide methods" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe")
        )

      val methods = List("GET", "POST", "PATCH", "PUT", "DELETE")
      val supportedMethods = response.headers.getMap("Access-Control-Allow-Methods").mkString(",").split(",")
      supportedMethods.diff(methods).isEmpty mustEqual true
    }

    "return access control headers in pre-flight" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map(
            "Access-Control-Request-Headers" -> "valid-header-1,valid-header-2,invalid-header-1",
            "Origin" -> "http://origin.safe"
          )
        )

      val headers = List("valid-header-1", "valid-header-2")
      val supportedHeaders = response.headers.getValue("Access-Control-Allow-Headers")
      supportedHeaders must contain theSameElementsAs headers
      response.headers.getStringValue("Access-Control-Allow-Origin") mustEqual "http://origin.safe"
    }

    "return origin headers in pre-flight" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe")
        )

      response.headers.getStringValue("Access-Control-Allow-Origin") mustEqual "http://origin.safe"
      response.headers.getStringValue("Access-Control-Allow-Credentials") mustEqual "true"
    }

    "reject invalid origins" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://bad.news")
        )

      response.code() mustEqual 403
      response.contentString() mustEqual "Origin not permitted."
    }

    "allow change to origins list" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://bad.origin")
        )

      response.code() mustEqual 403
      response.contentString() mustEqual "Origin not permitted."

      try {
        WebManager.setCORSAllowedOrigins(conf, CorsWhiteList(List("http://bad.origin")), EndpointType.EXTERNAL)
        val response =
          oneOff(
            s"http://localhost:$externalPort",
            "OPTIONS",
            "/api/v1/endpoint",
            """{"key":"value"}""",
            Map("Origin" -> "http://bad.origin")
          )

        response.code() mustEqual 200
      } finally {
        WebManager.setCORSAllowedOrigins(conf, AllowAll(), EndpointType.BOTH)
        WebManager.setCORSAllowedOrigins(conf, CorsWhiteList(externalOrigins), EndpointType.EXTERNAL)
      }
    }

    "allow Origins on a per-endpoint basis" in {
      var response =
        oneOff(
          s"http://localhost:$internalPort",
          "GET",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://other.origin.GET")
        )

      response.code() mustEqual 200

      response = oneOff(
        s"http://localhost:$internalPort",
        "GET",
        "/api/v1/endpoint",
        """{"key":"value"}""",
        Map("Origin" -> "http://other.origin.POST")
      )

      response.code() mustEqual 403

      response = oneOff(
        s"http://localhost:$internalPort",
        "POST",
        "/api/v1/endpoint",
        """{"key":"value"}""",
        Map("Origin" -> "http://other.origin.POST")
      )

      response.code() mustEqual 200
    }

    "OPTIONs call should be rejected if Origin isn't in any one of that path's endpoint's allowed origins" in {
      val response =
        oneOff(
          s"http://localhost:$internalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://other.origin.GET")
        )

      response.code() mustEqual 403
      response.contentString() mustEqual "Origin not permitted."
    }

    "return origin info on normal requests" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "OPTIONS",
          "/api/v1/endpoint",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe")
        )

      response.headers.getStringValue("Access-Control-Allow-Origin") mustEqual "http://origin.safe"
      response.headers.getStringValue("Access-Control-Allow-Credentials") mustEqual "true"
    }

    "websockets have support for CORS" in {
      val response =
        oneOff(
          s"http://localhost:$externalPort",
          "OPTIONS",
          "/ws/cors",
          """{"key":"value"}""",
          Map("Origin" -> "http://origin.safe", "Access-Control-Request-Headers" -> "Header-1,Header-2,Bad-Header")
        )
      response.headers.getStringValue("Access-Control-Allow-Origin") mustEqual "http://origin.safe"
      response.headers.getStringValue("Access-Control-Allow-Credentials") mustEqual "true"
      response.headers.getStringValue("Access-Control-Allow-Methods") mustEqual "WS"
      response.headers.getStringValue("Access-Control-Allow-Headers") mustEqual "Header-1,Header-2"

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
            headers.put("Origin", util.Arrays.asList("http://origin.safe"))
            ()
          }
        })
        .build()

      // Connect to the server endpoint
      val session =
        clientManager.connectToServer(endpoint, cec, new URI(s"ws://localhost:$externalPort/ws/cors"))

      session.getBasicRemote.sendText("Hello from client!")

      // Wait for the server to send a message
      val messageFromServer = Await.result(promise.future, 10.seconds)
      session.close()

      assert(messageFromServer == "Got message: [Hello from client!],Default-Header: [default]")

    }

    "rejects WS call if origin is unallowed" in {
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
            headers.put("Origin", util.Arrays.asList("http://bad.origin"))
            ()
          }
        })
        .build()

      // Fail as origin isn't in whitelist
      intercept[DeploymentException] {
        clientManager.connectToServer(endpoint, cec, new URI(s"ws://localhost:$externalPort/ws/cors"))
      }
    }
  }
}
