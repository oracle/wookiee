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

package com.webtrends.harness.component.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx._

/**
 * Created by wallinm on 12/9/14.
 */
trait WebSocket extends BaseInboundHandler {

  def WebSocketFrame(ctx: ChannelHandlerContext, frame: TextWebSocketFrame): Unit = {
    val request = frame.text()

    log.info(s"request: $request")
    ctx.writeAndFlush(new TextWebSocketFrame(request.toString.toUpperCase))
  }

  override def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame): Boolean = {
    var msgHandled = true
    try {
      frame match {
        case f: CloseWebSocketFrame =>
          handshaker match {
            case Some(hs) => hs.close(ctx.channel(), f.retain())
            case None => msgHandled = false
          }
        case f: PingWebSocketFrame => ctx.channel().writeAndFlush(new PongWebSocketFrame(f.content().retain()))
        case f: TextWebSocketFrame => WebSocketFrame(ctx, f)
        case _ => throw new UnsupportedOperationException(
          String.format("%s frame type not supported", frame.getClass.getName)
        )
      }
    } catch {
      case e: Throwable =>
        log.warn("exception in handleWebSocketFrame", e)
        sendError(ctx, s"error: ${e.getMessage}")
    }
    
    msgHandled
  }

  def sendError(ctx: ChannelHandlerContext, msg: String) {
    ctx.channel.write(new TextWebSocketFrame(msg))
    ctx.channel.flush()
    ctx.flush()
  }
}
