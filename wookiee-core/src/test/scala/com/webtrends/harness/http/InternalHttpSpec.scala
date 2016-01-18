package com.webtrends.harness.http

import java.net.{HttpURLConnection, URL}
import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorSystem}
import akka.testkit.TestKit
import akka.util.Timeout
import com.webtrends.harness.TestKitSpecificationWithJUnit
import com.webtrends.harness.service.messages.CheckHealth
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration.FiniteDuration

class InternalHttpSpec extends TestKitSpecificationWithJUnit(ActorSystem("test")) with InternalHttpClient {
  val port = 8123
  val path = "http://127.0.0.1:" + port + "/"
  val httpActor = system.actorOf(Props(classOf[SimpleHttpServer], port))

  // We need to make sure the httpActor has started up before trying to connect.
  implicit val timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
  Await.result(httpActor ? CheckHealth, timeout.duration)

  "Test handlers" should {
    "handle the get path /ping" in {
      val url = new URL(path + "ping")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content.substring(0, 5) mustEqual "pong:"
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }

}
