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

package com.webtrends.service

import com.webtrends.harness.component.netty.WebSocket
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx._

/**
 * Created by wallinm on 12/9/14.
 */
trait NettyExampleWebSocket extends WebSocket {

  override def WebSocketFrame(ctx: ChannelHandlerContext, frame: TextWebSocketFrame): Unit = {
    val request = frame.text()

    log.info(s"request: $request")
    ctx.writeAndFlush(new TextWebSocketFrame("Echo from example server: ".concat(request.toString)))
  }
}
