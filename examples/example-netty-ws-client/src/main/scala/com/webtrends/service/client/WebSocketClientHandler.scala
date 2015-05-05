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



import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpResponse
;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.util.CharsetUtil

/**
 * Created by wallinm on 12/23/14.
 */
class WebSocketClientHandler(handshaker: WebSocketClientHandshaker) extends SimpleChannelInboundHandler[AnyRef] {

  var handshakeFuture: Option[ChannelPromise] = None

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    this.handshakeFuture = Some(ctx.newPromise())
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    handshaker.handshake(ctx.channel())
  }


  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    System.out.println("WebSocket Client disconnected!")
  }


  override def channelRead0(ctx: ChannelHandlerContext, msg: AnyRef) {
    val ch = ctx.channel()
    if (!handshaker.isHandshakeComplete) {
      msg match {
        case req: FullHttpResponse =>
          handshaker.finishHandshake(ch, req)
          System.out.println("WebSocket Client connected!")
          handshakeFuture.get.setSuccess
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
            case f: TextWebSocketFrame => System.out.println("WebSocket Client received message: " + f.text())
            case f: PongWebSocketFrame => System.out.println("WebSocket Client received pong")
            case f: CloseWebSocketFrame =>
              System.out.println("WebSocket Client received closing")
              ch.close()
          }
      }
    }
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    if (!handshakeFuture.get.isDone()) {
      handshakeFuture.get.setFailure(cause)
    }
    ctx.close()
  }
}
