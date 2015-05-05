package com.webtrends.harness.http

import java.net.{HttpURLConnection, URL}

import akka.actor.{Props, ActorSystem}
import akka.testkit.TestKit
import com.webtrends.harness.TestKitSpecificationWithJUnit

class InternalHttpSpec extends TestKitSpecificationWithJUnit(ActorSystem("test")) with InternalHttpClient {
  val port = 8123
  val path = "http://127.0.0.1:" + port + "/"
  val httpActor = system.actorOf(Props(classOf[SimpleHttpServer], port))

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
