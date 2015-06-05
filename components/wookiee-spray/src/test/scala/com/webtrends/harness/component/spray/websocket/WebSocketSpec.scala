/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.websocket

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.webtrends.harness.component.spray.{TestKitSpecificationWithJUnit, SprayManager, SprayTestConfig}
import com.webtrends.harness.service.test.TestHarness
import spray.can.websocket.{SendStream, Send}
import spray.can.websocket.frame._
import spray.can.{Http, websocket}
import spray.http.HttpHeaders

/**
 * Created by wallinm on 4/22/15.
 */
class WebSocketSpec extends TestKitSpecificationWithJUnit(ActorSystem("test")) with WebSocketWorkerHelper {
  implicit def anyToSuccess[T](a: T): org.specs2.execute.Result = success

  implicit val timeout = Timeout(2000, TimeUnit.MILLISECONDS)
  val sys = TestHarness(SprayTestConfig.config, None, Some(Map("wookiee-spray" -> classOf[SprayManager])))
  implicit val actorSystem = TestHarness.system.get

  addWebSocketWorker("test", classOf[ServerWorker])
  addWebSocketWorker("test/caps", classOf[ServerWorkerCaps])
  addWebSocketWorker("$ver/api/account/$account", classOf[ServerWorkerBean])
  Thread.sleep(1000)
  sequential
  
  "WebSocket Server" should {

    "connect " in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/test")
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, basicReq)))

      val probe = TestProbe()
      probe.send(client, Send(TextFrame("Test 123")))
      TextFrame("Test 123") must be equalTo probe.expectMsg(TextFrame("Test 123"))
      probe.send(client, Send(CloseFrame()))
    }

    "connect with keys " in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/v1/api/account/35000")
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, basicReq)))

      val probe = TestProbe()
      probe.send(client, Send(TextFrame("Test 123")))
      TextFrame("Test 123 ver:v1 account:35000") must be equalTo probe.expectMsg(TextFrame("Test 123 ver:v1 account:35000"))
      probe.send(client, Send(CloseFrame()))
    }

    "connect with permessage-deflate" in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/test")
      val req = basicReq.withHeaders(HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate") :: basicReq.headers)
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, req)))

      val probe = TestProbe()
      probe.send(client, Send(TextFrame("Test 123")))
      TextFrame("Test 123") must be equalTo probe.expectMsg(TextFrame("Test 123"))
      probe.send(client, Send(CloseFrame()))
    }

    "reply with caps " in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/test/caps")
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, basicReq)))

      val probe = TestProbe()
      probe.send(client, Send(TextFrame("Test 123")))
      TextFrame("TEST 123") must be equalTo probe.expectMsg(TextFrame("TEST 123"))
      probe.send(client, Send(CloseFrame()))
    }

    "ping pong" in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/test")
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, basicReq)))

      val probe = TestProbe()
      probe.send(client, Send(PingFrame()))
      PongFrame() must be equalTo probe.expectMsg(PongFrame())
      probe.send(client, Send(CloseFrame()))
    }

    "Frame Stream" in {
      val connect = Http.Connect("localhost", 9091, false)
      val basicReq = websocket.basicHandshakeRepuset("/test")
      val client = actorSystem.actorOf(Props(new ClientWorker(connect, basicReq)))

      val probe = TestProbe()
      val frame = TextFrameStream(1, new ByteArrayInputStream("This is a very long Test 123".getBytes("UTF-8")))
      probe.send(client, SendStream(frame))
      TextFrame("This is a very long Test 123") must be equalTo probe.expectMsg(TextFrame("This is a very long Test 123"))
      probe.send(client, Send(CloseFrame()))
    }
  }
}
