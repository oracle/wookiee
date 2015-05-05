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

package com.webtrends.service.client

import java.net.URI

import com.webtrends.harness.component.netty.NettyService
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.Ready
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpClientCodec, DefaultHttpHeaders}
import io.netty.handler.codec.http.websocketx._

import scala.concurrent.Future

/**
 * Created by wallinm on 12/23/14.
 */
class WebSocketClient extends NettyService {

  /**
   * This is the receive expression
   *   Log when the Ready message is received
   */
  override def serviceReceive = ({
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
      // Give the netty server component time to start
      Thread sleep 2000
      log.info("Starting client")
      val thread = new Thread {
        override def run: Unit = {
          startClient
        }
      }
      thread.start()
  }: Receive) orElse super.serviceReceive

  /**
   * Return the health of this service
   */
  override def getHealth: Future[HealthComponent] = {
    Future {
      HealthComponent("WebSocketClientExample", ComponentState.NORMAL, "WebSocket client example is running")
    }
  }
  
  def startClient: Unit = {
    val uri = new URI("ws://127.0.0.1:9091/stream")
    val ping = Array[Byte](8,1,8,1)

    val group = new NioEventLoopGroup()
    try {
      val handler = new WebSocketClientHandler(WebSocketClientHandshakerFactory.newHandshaker(
        uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders))
      val b = new Bootstrap()
      val port = uri.getPort

      b.group(group)
        .channel(classOf[NioSocketChannel])
        .handler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel) {
            val p = ch.pipeline()
            p.addLast(new HttpClientCodec(),
                      new HttpObjectAggregator(8192),
                      handler)
        }
      })

      val ch = b.connect(uri.getHost, port).sync.channel
      handler.handshakeFuture.get.sync

      var end = false
      var msg = "Hello, there"
      while (!end) {
        msg match {
          case null =>
            end = true
          case "bye" =>
            ch.writeAndFlush(new CloseWebSocketFrame())
            ch.closeFuture().sync()
            end = true
          case "ping" =>
            val frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(ping))
            ch.writeAndFlush(frame)
            msg = "bye"
          case _ =>
            val frame = new TextWebSocketFrame(msg)
            ch.writeAndFlush(frame)
            msg = "ping"
        }
      }
    } finally {
        group.shutdownGracefully()
    }
  }
}

