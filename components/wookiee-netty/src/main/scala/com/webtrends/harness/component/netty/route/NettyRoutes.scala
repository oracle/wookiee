package com.webtrends.harness.component.netty.route

import akka.actor.ActorRef
import com.webtrends.harness.command.{CommandBean, CommandResponse, Command}
import com.webtrends.harness.component.netty.BaseInboundHandler
import com.webtrends.harness.component.netty.handler.HandlerManager
import com.webtrends.harness.component.netty.route.NettyCommand.HandleHttpRequest
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpRequest, HttpMethod}
import io.netty.handler.codec.http.HttpMethod._

import scala.util.{Success, Failure}

/**
 * @author Michael Cuthbert on 6/2/15.
 */
@Sharable
private[route] class NettyHandler(commandRef:ActorRef, paths:Map[String, String], method:HttpMethod) extends BaseInboundHandler {
  /**
   * Override this function in order to process http requests.
   * @param ctx
   * @param req
   * @return true if this function handled the request; otherwise, false
   */
  override def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest): Boolean = {
    (req.getMethod, req.getUri) match {
      case (this.method, path) =>
        paths.foreach {
          case (name, testPath) =>
            Command.matchPath(testPath, path.substring(1)) match {
              case Some(bean) =>
                commandRef ! HandleHttpRequest(ctx, req, Some(bean))
                return true
              case None => // ignore and go on to the next path to match
            }
        }

      case _ => // ignore and we will leave this as unhandled
    }
    return false
  }
}

private[route] trait NettyRoutes extends HandlerFunctions {
  this:Command =>
  import context.dispatcher

  def innerExecute[T<:AnyRef:Manifest](ctx:ChannelHandlerContext, req:FullHttpRequest, bean:Option[CommandBean]) = {
    execute(bean).mapTo[CommandResponse[T]] onComplete {
      case Success(s) =>
        s.data match {
          case Some(value) => sendContent(ctx, req, value.toString, s.responseType)
          case None => sendNoContent(ctx, req)
        }

      case Failure(f) => sendFailure(ctx, req, f)
    }
  }
}

trait NettyGet extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_GET", new NettyHandler(self, paths, GET))
}

trait NettyPut extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_PUT", new NettyHandler(self, paths, PUT))
}

trait NettyPost extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_POST", new NettyHandler(self, paths, POST))
}

trait NettyOptions extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_OPTIONS", new NettyHandler(self, paths, OPTIONS))
}

trait NettyHead extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_HEAD", new NettyHandler(self, paths, HEAD))
}

trait NettyPatch extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_PATCH", new NettyHandler(self, paths, PATCH))
}

trait NettyDelete extends NettyRoutes {
  this:NettyCommand =>
  HandlerManager.addGetHandler(s"${commandName}_DELETE", new NettyHandler(self, paths, DELETE))
}


