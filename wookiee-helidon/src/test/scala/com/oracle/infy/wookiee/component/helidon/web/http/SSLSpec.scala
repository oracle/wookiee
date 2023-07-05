package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.component.helidon.util.EndpointTestHelper
import com.oracle.infy.wookiee.component.helidon.util.TestObjects.{InputObject, OutputObject}
import com.oracle.infy.wookiee.component.helidon.web.WookieeEndpoints
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Paths
import scala.concurrent.Future

class SSLSpec extends EndpointTestHelper {

  override implicit def conf: Config =
    super
      .conf
      .withFallback(ConfigFactory.parseString {
        val resource = getClass.getResource("/test-ssl-key.jks")
        val keyPath = Paths.get(resource.toURI).toFile.getAbsolutePath.replaceAll("\\\\", "/")

        s"""
        |wookiee-helidon {
        |  web {
        |    secure {
        |      keystore-path = "$keyPath"
        |      keystore-passphrase = "test_key_pass"
        |    }
        |  }
        |}
        |""".stripMargin
      })

  override def registerEndpoints(manager: HelidonManager): Unit = {
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
