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

import com.webtrends.harness.component.netty.config.NettyServerSettings
import com.webtrends.harness.component.netty.CoreNettyServer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelOption}
import io.netty.handler.logging.{LogLevel, LoggingHandler}

/**
 * Created by wallinm on 12/9/14.
 */
object SocketServer {
  def apply(settings: NettyServerSettings, nettyServer: CoreNettyServer): SocketServer = {
    val bossGroup = new NioEventLoopGroup(1)
    val workerGroup = new NioEventLoopGroup()

    try {
      val b = new ServerBootstrap()
        .group(bossGroup,workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, settings.tcpNoDelay)
        .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, settings.tcpKeepAlive)
        .handler(new LoggingHandler(LogLevel.INFO))
      if ( settings.tcpSndBufferSize > 0 ) {
        b.childOption[java.lang.Integer](ChannelOption.SO_SNDBUF, settings.tcpSndBufferSize.toInt)
      }
      if ( settings.tcpRcvBufferSize > 0 ) {
        b.childOption[java.lang.Integer](ChannelOption.SO_RCVBUF, settings.tcpRcvBufferSize.toInt)
      }

      b.childHandler(new SocketServerInitializer(settings, nettyServer))

      new SocketServer(b.bind(settings.port).sync().channel(), bossGroup, workerGroup)
    } catch {
      case e: Throwable =>
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        throw new RuntimeException(s"unable to bootstrap ${getClass.getName} ${e}")
    }
  }
}

class SocketServer(serverCh: Channel, bossGroup: NioEventLoopGroup, workerGroup: NioEventLoopGroup) {
  def blockAndClose(): Unit = {
    serverCh.closeFuture().sync()

    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
  }

  def close(): Unit = {
    serverCh.close()
    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
  }

}
