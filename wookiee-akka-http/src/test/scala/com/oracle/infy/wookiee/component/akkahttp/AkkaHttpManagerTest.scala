package com.oracle.infy.wookiee.component.akkahttp

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.oracle.infy.wookiee.component.akkahttp.routes.InternalAkkaHttpActor
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory.defaultReference
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor

class AkkaHttpManagerTest extends AnyWordSpecLike with Matchers {

  "Akka Http Manager" should {
    val baseConfig =
      ConfigFactory.parseString("""
        |wookiee-akka-http {
        |  // For healtcheck, metric, lb and other endpoints
        |  internal-server {
        |    enabled = true
        |    interface = "localhost"
        |    http-port = 8080
        |  }
        |
        |  external-server {
        |    interface = "localhost"
        |    http-port = 8082
        |  }
        |
        |  static-content {
        |    root-path = "html"
        |    type = "jar"
        |  }
        |
        |  websocket-keep-alives {
        |    enabled = false
        |    interval = 30s
        |  }
        |
        |  access-logging {
        |    enabled = true
        |  }
        |
        |  manager = "com.webtrends.harness.component.akkahttp.AkkaHttpManager"
        |  enabled = true
        |  dynamic-component = true
        |
        |  default-headers: [ {"X-Content-Type-Options": "nosniff"} ]
        |}
        |
        |akka {
        |  http {
        |    host-connection-pool.client.idle-timeout = 300s
        |    server {
        |      idle-timeout = 300s
        |      request-timeout = 300s
        |    }
        |  }
        |}
      """.stripMargin)

    "read a valid config" in {
      val settings = AkkaHttpSettings(baseConfig.withFallback(defaultReference(getClass.getClassLoader)))

      settings.internal.httpsPort mustEqual None
      settings.internal.port mustEqual 8080
      settings.external.httpsPort mustEqual None
    }

    val httpsConfig = ConfigFactory.parseString("""
        |wookiee-akka-http {
        |  internal-server {
        |    https-port = 9090
        |  }
        |
        |  external-server {
        |    https-port = 9091
        |  }
        |}
      """.stripMargin).withFallback(baseConfig).withFallback(defaultReference(getClass.getClassLoader))
    val httpSettings = AkkaHttpSettings(httpsConfig)

    "extract https ports when specified" in {
      httpSettings.internal.httpsPort mustEqual Some(9090)
      httpSettings.external.httpsPort mustEqual Some(9091)
    }

    "host endpoints" in {
      val actorSystem = ActorSystem("internal-test", baseConfig)
      implicit val ex: ExecutionContextExecutor = actorSystem.dispatcher
      implicit val materializer: Materializer = Materializer.matFromSystem(actorSystem)

      try {
        val internal = actorSystem.actorOf(InternalAkkaHttpActor.props(httpSettings.internal))
        actorSystem.stop(internal)
      } finally {
        actorSystem.terminate()
        ()
      }
    }
  }
}
