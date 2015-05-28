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

import java.io.{File, FileInputStream}
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.activation.MimetypesFileTypeMap

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.component.{ComponentRequest, ComponentResponse}
import com.webtrends.harness.component.messages._
import com.webtrends.harness.component.netty.config.NettyServerSettings
import com.webtrends.harness.health.{ComponentState, ApplicationHealth, HealthResponseType}
import com.webtrends.harness.service.meta.ServiceMetaData
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaders.Names
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.stream.ChunkedStream
import net.liftweb.json._
import net.liftweb.json.Extraction._
import net.liftweb.json.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.joda.time.{DateTimeZone, DateTime}
import scala.util.{Failure, Success}

/**
 * Created by wallinm on 12/9/14.
 */
protected class CoreHttpHandler(settings: NettyServerSettings, worker: ActorRef) extends BaseInboundHandler {
  implicit val timeout = Timeout(settings.requestTimeout, TimeUnit.SECONDS)
  implicit val formats = DefaultFormats + new EnumNameSerializer(ComponentState) ++ JodaTimeSerializers.all

  // TODO need to add Spray's equivalent of directives in here...like CIDR rules
  override def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest): Boolean = {
    import scala.concurrent.ExecutionContext.Implicits.global

    if (!req.getDecoderResult.isSuccess) {
      sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.getProtocolVersion(), BAD_REQUEST))
    }

    (req.getMethod, req.getUri) match {
      case (GET, "/favicon.ico") =>
        sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.getProtocolVersion(), NO_CONTENT))
      case (GET, "/ping") =>
        sendContent(ctx, req, "pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString), "text/plain")
      case (GET, "/metrics") =>
        (worker ? GetSystemInfo("wookiee-metrics", ComponentRequest(StatusRequest()))).mapTo[ComponentResponse[JValue]] onComplete {
          case Success(s) => sendContent(ctx, req, JsonAST.compactRender(s.resp), "application/json")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/services") =>
        (worker ? GetServiceInfo()).mapTo[Seq[ServiceMetaData]] onComplete {
          case Success(s) => sendContent(ctx, req, compactRender(decompose(s)), "application/json")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/cluster") =>
        (worker ? GetSystemInfo("wookiee-cluster", ComponentRequest(ClusterState(), Some("cluster")))).mapTo[ComponentResponse[JValue]] onComplete {
          case Success(s) => sendContent(ctx, req, JsonAST.compactRender(s.resp), "application/json")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/cluster/discovery") =>
        (worker ? GetSystemInfo("wookiee-cluster", ComponentRequest(Subscriptions()))).mapTo[ComponentResponse[JValue]] onComplete {
          case Success(s) => sendContent(ctx, req, JsonAST.compactRender(s.resp), "application/json")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/healthcheck/full") =>
        (worker ? GetHealth(HealthResponseType.FULL)).mapTo[ApplicationHealth] onComplete {
          case Success(s) => sendContent(ctx, req, compactRender(decompose(s)), "application/json")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/healthcheck/lb") =>
        (worker ? GetHealth(HealthResponseType.LB)).mapTo[String] onComplete {
          case Success(s) => sendContent(ctx, req, s, "text/plain")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, "/healthcheck/nagios") =>
        (worker ? GetHealth(HealthResponseType.NAGIOS)).mapTo[String] onComplete {
          case Success(s) => sendContent(ctx, req, s, "text/plain")
          case Failure(f) => sendFailure(ctx, req, f)
        }
      case (GET, settings.websocketPath) if settings.websocketPath.length > 1 =>
        val wsPath = s"ws://${req.headers().get(HOST)}"
        val wsFactory = new WebSocketServerHandshakerFactory(wsPath, null, false)
        handshaker = Some(wsFactory.newHandshaker(req))
        handshaker match {
          case Some(hs) => hs.handshake(ctx.channel(), req)
          case None => WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
        }
      case _ => staticFile(ctx, req)
    }

    // All cases were handled
    true
  }

  def staticFile(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    if (settings.fileLocation.isEmpty) {
      sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.getProtocolVersion, NOT_FOUND))
      return
    }

    if (req.getMethod != HttpMethod.GET) {
      sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.getProtocolVersion, METHOD_NOT_ALLOWED))
      return
    }

    // get input stream from file or from jar
    val url = new URL(s"file://${req.getUri}")
    val is = try {

      // load static from file or jar
      val inputStream = settings.fileSource match {
        case "file" => new FileInputStream(new File(s"${settings.fileLocation}${url.getPath}"))
        case "jar" => getClass.getResourceAsStream(s"${settings.fileLocation}${url.getPath}")
      }

      if (inputStream == null)
        throw new NullPointerException
      inputStream
    } catch {
      case e: Throwable =>
        sendHttpResponse(ctx, req, new DefaultFullHttpResponse(req.getProtocolVersion, NOT_FOUND))
        return
    }

    // write headers
    val response = new DefaultHttpResponse(req.getProtocolVersion, OK)
    val mimeTypesMap = new MimetypesFileTypeMap()
    response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(url.getPath))
    response.headers().set(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)
    ctx.write(response)

    // write stream
    ctx.write(new HttpChunkedInput(new ChunkedStream(is)))
    val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    if (!HttpHeaders.isKeepAlive(req)) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
  }
}
