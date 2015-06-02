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

import com.webtrends.harness.logging.LoggingAdapter
import io.netty.channel.{ChannelHandlerContext, ChannelPromise, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx._
import io.netty.util.CharsetUtil

/**
 * Created by wallinm on 12/23/14.
 */
class TestWebSocketClientHandler(handshaker: WebSocketClientHandshaker, channelData: ChannelData, connectionMonitor: AnyRef)
  extends SimpleChannelInboundHandler[AnyRef]
  with LoggingAdapter{

  var handshakeFuture: Option[ChannelPromise] = None

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    log.debug("WebSocket Client handler added!")    
    this.handshakeFuture = Some(ctx.newPromise())
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    handshaker.handshake(ctx.channel())
  }


  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    log.debug("WebSocket Client disconnected!")
    
    channelData.isConnected = Some(false)
    connectionMonitor.synchronized {
      connectionMonitor.notifyAll()
    }
  }


  override def channelRead0(ctx: ChannelHandlerContext, msg: AnyRef) {
    val ch = ctx.channel
    
    if (!handshaker.isHandshakeComplete) {
      msg match {
        case req: FullHttpResponse =>
          try {
            handshaker.finishHandshake(ch, req)
            log.debug("WebSocket Client connected!")
            channelData.isConnected = Some(true)
            handshakeFuture.get.setSuccess
          } catch {
            case ex: Throwable =>
              log.debug("Error connecting to WebSocket Server", ex)
              channelData.isConnected = Some(false)
          }
        case _ =>
      }
    } else {
      msg match {
        case req: FullHttpResponse =>
          throw new IllegalStateException(
            "Unexpected FullHttpResponse (getStatus=" + req.getStatus +
              ", content=" + req.content.toString(CharsetUtil.UTF_8) + ')')
        case frame: WebSocketFrame =>
          frame match {
            case f: TextWebSocketFrame =>
              log.debug("WebSocket Client received message: " + f.text())
              channelData.textBuffer.append(f.text())
              channelData.hasReplied = true
            case f: PongWebSocketFrame => 
              log.debug("WebSocket Client received pong")
              channelData.hasReplied = true
              channelData.hasReceivedPong = true;
            case f: CloseWebSocketFrame =>
              log.debug("WebSocket Client received closing")
              ch.close
          }
      }
    }
    
    connectionMonitor.synchronized {
      connectionMonitor.notifyAll
    }
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    if (!handshakeFuture.get.isDone) {
      handshakeFuture.get.setFailure(cause)
    }
    channelData.isConnected = Some(false)
    ctx.channel.close
  }
}
