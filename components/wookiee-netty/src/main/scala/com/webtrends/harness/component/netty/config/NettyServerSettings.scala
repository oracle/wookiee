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
package com.webtrends.harness.component.netty.config

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

/**
 * *
 * @param port The port on which to run the server
 * @param serverHeader The host name to set in HttpHeader
 * @param requestTimeout The time allowed to respond to a request
 * @param idleTimeout The time after which an idle connection is closed
 * @param tcpNoDelay Enables the TCP_NODELAY flag
 * @param tcpKeepAlive Enables TCP keep alive
 * @param tcpSndBufferSize Set the send buffer size
 * @param tcpRcvBufferSize Set the receive buffer size
 */
case class NettyServerSettings(port: Int = 9091,
                             websocketPath: String = "/stream",
                             serverHeader: String = "harness",
                             requestTimeout: Long = 60L,
                             idleTimeout: Long = 120L,
                             tcpNoDelay: Boolean = false,
                             tcpKeepAlive: Boolean = false,
                             tcpSndBufferSize: Long = 0,
                             tcpRcvBufferSize: Long = 0,
                             fileSource:String="file",
                             fileLocation:String="src/main/resources/html/") {
}


object NettyServerSettings {
    def apply(config: Config): NettyServerSettings = {
      NettyServerSettings(config getInt "port",
        config getString "websocket-path",
        config getString "server-header",
        config getDuration("request-timeout", TimeUnit.SECONDS),
        config getDuration("idle-timeout", TimeUnit.SECONDS),
        config getBoolean "tcp.tcp-nodelay",
        config getBoolean "tcp.tcp-keepalive",
        config getBytes "tcp.send-buffer-size",
        config getBytes "tcp.receive-buffer-size",
        config getString "static-files.filesource",
        config getString "static-files.location")
    }
}
