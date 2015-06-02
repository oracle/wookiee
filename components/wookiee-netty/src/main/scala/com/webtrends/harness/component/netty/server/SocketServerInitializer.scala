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

package com.webtrends.harness.component.netty.server

import java.util.concurrent.TimeUnit

import com.webtrends.harness.component.netty.CoreNettyServer
import com.webtrends.harness.component.netty.config.NettyServerSettings
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.handler.timeout.IdleStateHandler

/**
 * Created by wallinm on 12/9/14.
 */
class SocketServerInitializer(settings: NettyServerSettings, baseServer: CoreNettyServer) extends ChannelInitializer[SocketChannel] {
  override def initChannel(ch: SocketChannel) {
    val pipeline = ch.pipeline()
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(65536))
    pipeline.addLast(new ChunkedWriteHandler)
    pipeline.addLast(new IdleStateHandler(settings.idleTimeout, 0, 0, TimeUnit.SECONDS))
    pipeline.addLast(new TimeoutHandler())
    baseServer.getHandlers.values.foreach(h => pipeline.addLast(h))
  }
}
