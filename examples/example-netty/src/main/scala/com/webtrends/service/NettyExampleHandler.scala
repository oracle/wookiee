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

import com.webtrends.harness.component.netty.BaseInboundHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.FullHttpRequest
import org.joda.time.{DateTimeZone, DateTime}

/**
 * Created by wallinm on 12/17/14.
 */
class NettyExampleHandler extends BaseInboundHandler {

  override def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest) : Boolean =  {
    var msgHandled = true
    
    (req.getMethod, req.getUri) match {
      case (GET, "/nettyexample/ping") =>
        sendContent(ctx, req, "pong: from netty example ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString), "text/plain")
      case (GET, "/nettyexample/ping/json") =>
        val t = "".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString)
        sendContent(ctx, req,  s""" {"message": "pong from nettyexample", "time": "$t"} """, "application/json")
      case _ => msgHandled = false
    }
    
    msgHandled
  }
}
