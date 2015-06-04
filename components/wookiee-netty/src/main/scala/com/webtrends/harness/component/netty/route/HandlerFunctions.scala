package com.webtrends.harness.component.netty.route

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{FullHttpResponse, HttpHeaders, DefaultFullHttpResponse, FullHttpRequest}
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.util.CharsetUtil

/**
 * @author Michael Cuthbert on 6/4/15.
 */
trait HandlerFunctions {
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

  def sendNoContent(ctx: ChannelHandlerContext, req: FullHttpRequest) {
    val res = new DefaultFullHttpResponse(req.getProtocolVersion, NO_CONTENT)
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
}
