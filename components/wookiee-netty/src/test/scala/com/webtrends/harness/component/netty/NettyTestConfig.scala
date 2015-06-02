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

import com.typesafe.config.ConfigFactory
import com.webtrends.harness.service.test.config.TestConfig

/**
 * Created by wallinm on 1/14/15.
 */
object NettyTestConfig {
  val config = TestConfig.conf("""
      wookiee-netty {
        manager = "com.webtrends.harness.component.netty.NettyManager"
        
        # The port on which to run the server
        port = 9091
        # The URI to create a websocket connection
        websocket-path = "/stream"
        # The host name to set in HttpHeader
        server-header = "harness"
        # The time allowed to respond to a request
        request-timeout = 60s
        # The time after which an idle connection will be closed
        idle-timeout = 120s
        static-files {
          # Whether static files are pulled from JAR or File
          filesource = "file"
          location = "src/main/resources/html/"
        }

        tcp {
          # Enables the TCP_NODELAY flag, i.e. disables Nagle.s algorithm
          tcp-nodelay = off
          # Enables TCP Keepalive, subject to the O/S kernel.s configuration
          tcp-keepalive = off
          # Sets the send buffer size of the Sockets,
          # set to 0b for platform default
          send-buffer-size = 0b
          # Sets the receive buffer size of the Sockets,
          # set to 0b for platform default
          receive-buffer-size = 0b
        }
      }
      akka.actor.deployment {
        /system/component/wookiee-netty/netty-server/netty-worker {
          router = round-robin
          nr-of-instances = 3
        }
      }
      """).withFallback(ConfigFactory.load("conf/application.conf")).resolve()
}