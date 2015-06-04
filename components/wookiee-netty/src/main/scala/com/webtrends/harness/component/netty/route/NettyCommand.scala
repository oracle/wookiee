package com.webtrends.harness.component.netty.route

import com.webtrends.harness.command.{CommandBean, Command}
import com.webtrends.harness.component.netty.route.NettyCommand.HandleHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest

/**
 * @author Michael Cuthbert on 6/4/15.
 */
abstract class NettyCommand extends Command {
  this:NettyRoutes =>

  override def receive = super.receive orElse {
    case HandleHttpRequest(ctx, req, bean) => handleHttpRequest(ctx, req, bean)
  }

  def handleHttpRequest(ctx:ChannelHandlerContext, req:FullHttpRequest, bean:Option[CommandBean]) = {
    innerExecute(ctx, req, bean)
  }
}

object NettyCommand {
  case class HandleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, bean:Option[CommandBean]=None)
}
