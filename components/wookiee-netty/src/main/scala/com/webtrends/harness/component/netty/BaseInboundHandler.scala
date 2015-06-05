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

import com.webtrends.harness.component.netty.route.HandlerFunctions
import com.webtrends.harness.logging.Logger
import io.netty.channel.{ChannelInboundHandlerAdapter, ChannelHandlerContext}
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.websocketx.{WebSocketFrame, WebSocketServerHandshaker}
import io.netty.util.ReferenceCountUtil

/**
 * Created by wallinm on 12/22/14.
 */
abstract class BaseInboundHandler() extends ChannelInboundHandlerAdapter with HandlerFunctions {

  protected val log = Logger.getLogger(getClass)
  protected var handshaker: Option[WebSocketServerHandshaker] = None

  override final def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {
    var msgHandled = false

    try {
      msg match {
        // http requests
        case req: FullHttpRequest => msgHandled = handleHttpRequest(ctx, req)
        // websocket frames
        case frame: WebSocketFrame => msgHandled = handleWebSocketFrame(ctx, frame)
      }
    } finally {
      if (!msgHandled) {
        ctx.fireChannelRead(msg)
      } else {
        ReferenceCountUtil.release(msg)
      }
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close
  }
}
