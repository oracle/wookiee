package com.oracle.infy.wookiee.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.service.messages.CheckHealth
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.{HttpURLConnection, URL}
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

class InternalHttpSpec
    extends TestKit(ActorSystem("internal"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with InternalHttpClient {
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

      resp.status shouldEqual "200"
      resp.content.length should be > 0
      resp.content.substring(0, 5) shouldEqual "pong:"
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
