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

package com.webtrends.harness.component.netty.websocket

import java.util.concurrent.TimeUnit

import akka.testkit.TestProbe
import akka.util.Timeout
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.component.netty.{NettyTestConfig, NettyManager}
import com.webtrends.harness.service.test.TestHarness
import org.specs2.mutable.SpecificationWithJUnit

/**
 * Created by wallinm on 1/8/15.
 */
class WebSocketSpec extends SpecificationWithJUnit {
  implicit val timeout = Timeout(2000, TimeUnit.MILLISECONDS)
  val sys = TestHarness(NettyTestConfig.config, None, Some(Map("wookiee-netty" -> classOf[NettyManager])))

  implicit val actorSystem = TestHarness.system.get

  val nettyManager = sys.getComponent("wookiee-netty").get
  val probe = TestProbe()

  sequential
  
  "WebSocket Server" should {
    val ws = new TestWebSocketClient("http://127.0.0.1:9091/stream")

    "connect " in {
      ws.connect()
      
      ws.isConnected mustEqual true
    }
    
    "send and receive data " in {
      ws.sendText("This is a test")
      
      ws.getReceivedText() mustEqual "THIS IS A TEST"
    }
    
    "send ping and receive pong " in {
      ws.sendPing
      
      ws.getHasReceivedPong mustEqual true
    }
    
    "disconnect " in {
      ws.disconnect
      ws.isConnected() mustEqual false
    }
    

    "should not connect with bad URL" in {
      val wsBad = new TestWebSocketClient("http://127.0.0.1:9091/streaming")
      ws.isConnected() mustEqual false
    }
  }
}
