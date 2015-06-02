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

import com.webtrends.harness.logging.Logger
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelInboundHandlerAdapter, ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{FullHttpResponse, HttpHeaders, DefaultFullHttpResponse, FullHttpRequest}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame, WebSocketServerHandshaker}
import io.netty.util.{ReferenceCountUtil, CharsetUtil}

/**
 * Created by wallinm on 12/22/14.
 */
abstract class BaseInboundHandler() extends ChannelInboundHandlerAdapter {

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

  /**
   * Override this function in order to process http requests. 
   * @param ctx
   * @param req
   * @return true if this function handled the request; otherwise, false
   */
  def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest): Boolean = {false}

  /**
   * Override this function in order to process websocket frames 
   * @param ctx
   * @param frame
   * @return true if this function handled the frame; otherwise, false
   */
  def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame): Boolean = {false}

  /**
   * Sends an HttpResponse 
   * @param ctx ChannelHandlerContext
   * @param req The request which is being responded to
   * @param content The content for the response
   * @param contentType The content type
   */
  def sendContent(ctx: ChannelHandlerContext, req: FullHttpRequest, content: String, contentType: String) {
    val buf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
    val res = new DefaultFullHttpResponse(req.getProtocolVersion, OK, buf)
    res.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType.concat("; charset=UTF-8"))
    sendHttpResponse(ctx, req, res)
  }

  /**
   * Sends an HttpResponse with a status of 500
   * @param ctx ChannelHandlerContext
   * @param req The request which is being responded to
   * @param error The error which caused the error response
   */
  def sendFailure(ctx: ChannelHandlerContext, req: FullHttpRequest, error: Throwable) {
    val content = Unpooled.copiedBuffer(s"error: ${error.getMessage}", CharsetUtil.UTF_8)
    val res = new DefaultFullHttpResponse(req.getProtocolVersion, INTERNAL_SERVER_ERROR, content)
    res.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
    sendHttpResponse(ctx, req, res)
  }

  /**
   * Sends an HttpResponse
   * @param ctx ChannelHandlerContext
   * @param req The request being responded to
   * @param res The response to be sent
   */
  def sendHttpResponse(ctx: ChannelHandlerContext, req: FullHttpRequest, res: FullHttpResponse) {
    val keepAlive = HttpHeaders.isKeepAlive(req)

    HttpHeaders.setContentLength(res, res.content().readableBytes())

    res.getStatus.code match {
      case 200 =>
      case _ =>
        val buf = Unpooled.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8)
        res.content().writeBytes(buf)
        buf.release()
        HttpHeaders.setContentLength(res, res.content().readableBytes())
    }

    val f = ctx.channel().writeAndFlush(res)
    if (!keepAlive || res.getStatus.code() != 200) {
      f.addListener(ChannelFutureListener.CLOSE)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close
  }
}
