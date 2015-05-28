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

import java.net.URI

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpClientCodec, HttpObjectAggregator}

/**
 * Created by wallinm on 1/14/15.
 */
class TestWebSocketClient(url: String) {
  val group = new NioEventLoopGroup()
  val b = new Bootstrap()
    .group(group)
    .channel(classOf[NioSocketChannel])
  
  val uri = new URI(url)

  val channelData = new ChannelData
  val monitor = new AnyRef
  val handshaker = WebSocketClientHandshakerFactory.newHandshaker(
    new URI(url), WebSocketVersion.V13, null, false, new DefaultHttpHeaders)
  val handler = new TestWebSocketClientHandler(handshaker, channelData, monitor)

  b.handler(new ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel) {
      val p = ch.pipeline()
      p.addLast(new HttpClientCodec(),
        new HttpObjectAggregator(8192),
        handler)}
    })
    
  val ping = Array[Byte](8,1,8,1)

  var channel: Channel = null

  def connect() {
    channelData.isConnected = None

    channel = b.connect(uri.getHost, uri.getPort).syncUninterruptibly.channel
    handler.handshakeFuture.get.syncUninterruptibly

    monitor.synchronized {
      while (channelData.isConnected.isEmpty) {
        monitor.wait()
      }
    }
  }

  /**
   * Disconnect from the server
   */
  def disconnect() {
    channel match {
      case null =>
      case _ =>
        channel.writeAndFlush(new CloseWebSocketFrame)
        channel.closeFuture.awaitUninterruptibly
        group.shutdownGracefully()
    }
  }

  def isConnected(): Boolean = {
    if (channelData.isConnected.isEmpty) false else channelData.isConnected.get
  }
  
  def sendText(text: String): Unit = {
    channelData.hasReplied = false;
    channel.writeAndFlush(new TextWebSocketFrame(text))
    waitForResponse
  }
  
  def getReceivedText(): String = {
    channelData.textBuffer.toString
  }
  
  def sendPing(): Unit = {
    channelData.hasReplied = false;
    channelData.hasReceivedPong = false;
    channel.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(ping)))
    waitForResponse
  }
  
  def getHasReceivedPong: Boolean = {
    channelData.hasReceivedPong
  }
  
  def waitForResponse(): Unit = {
    monitor.synchronized {
      while(!channelData.hasReplied) {
        monitor.wait
      }
    }
  }
}

/**
 * Data associated with a specific channel
 */
class ChannelData() {
  var isConnected: Option[Boolean] = None
  var hasReplied = false
  var hasReceivedPong = false
  val textBuffer = new StringBuilder()
  val binaryBuffer = scala.collection.mutable.ListBuffer[Byte]()
}
