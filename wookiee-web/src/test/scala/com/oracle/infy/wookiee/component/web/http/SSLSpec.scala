package com.oracle.infy.wookiee.component.web.http

import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.web.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.component.web.{WebManager, WookieeEndpoints}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext, Future}

class SSLSpec extends EndpointTestHelper {
  implicit val ec: ExecutionContext = ThreadUtil.createEC("helidon-manager-test")

  override implicit def conf: Config =
    super
      .conf
      .withFallback(ConfigFactory.parseString {
        val resource = getClass.getResource("/test-ssl-key.jks")
        val keyPath = Paths.get(resource.toURI).toFile.getAbsolutePath.replaceAll("\\\\", "/")

        s"""
        |wookiee-web {
        |  secure {
        |    keystore-path = "$keyPath"
        |    keystore-passphrase = "test_key_pass"
        |  }
        |}
        |""".stripMargin
      })

  override def registerEndpoints(manager: WebManager): Unit = {
    WookieeEndpoints.registerEndpoint[InputObject, OutputObject](
      "basic-endpoint",
      "/basic/endpoint",
      "GET",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(InputObject("basic"))
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

  "Wookiee Helidon with SSL Support" should {
    "support SSL via HTTPS" in {
      // Do nothing, just make sure the server starts up
    }
  }
}
