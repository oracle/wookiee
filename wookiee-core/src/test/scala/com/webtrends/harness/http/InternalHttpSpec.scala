package com.webtrends.harness.http

import java.net.{HttpURLConnection, URL}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.webtrends.harness.service.messages.CheckHealth
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

class InternalHttpSpec extends TestKit(ActorSystem("internal")) with WordSpecLike with MustMatchers with BeforeAndAfterAll with InternalHttpClient {
  val port = 8123
  val path: String = "http://127.0.0.1:" + port + "/"
  val httpActor: ActorRef = system.actorOf(Props(classOf[SimpleHttpServer], port))

  // We need to make sure the httpActor has started up before trying to connect.
  implicit val timeout: Timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
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

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
