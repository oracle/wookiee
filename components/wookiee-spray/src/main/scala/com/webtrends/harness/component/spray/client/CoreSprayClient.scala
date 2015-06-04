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
package com.webtrends.harness.component.spray.client

import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.spray.SprayManager
import com.webtrends.harness.utils.ConfigUtil
import spray.can.server.UHttp
import spray.http._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.{ActorSystem, Props, Status}
import scala.concurrent.{Promise, Await, Future}
import spray.client.pipelining._
import spray.httpx.encoding.Gzip
import spray.http.HttpMethods._
import akka.pattern._
import scala.util.control.NonFatal
import net.liftweb.json._
import net.liftweb.json.Extraction.decompose
import akka.io.IO
import spray.can.Http
import spray.httpx.marshalling.Marshaller
import scala.util.Failure
import spray.http.HttpResponse
import scala.util.Success
import spray.http.HttpRequest

/**
 * This is a spray implementation of the HttpClient, Spray will handle pooling and connection management.
 */
object CoreSprayClient {
  def props: Props = Props[CoreSprayClient]
}

class CoreSprayClient extends HActor with HttpLiftSupport {
  import context.dispatcher
  implicit val system:ActorSystem = context.system

  // 60 seconds is the default that spray uses, so if the config value is not set we won't change the default
  implicit val futureTimeout = ConfigUtil.getDefaultTimeout(context.system.settings.config, SprayManager.KeyHttpClientTimeout, 60 seconds)

  val pipeline:HttpRequest => Future[HttpResponse] = (
    encode(Gzip)
      ~> sendReceive(IO(UHttp))
      ~> decode(Gzip)
    )

  /**
   * Will route messages to correct behavior, mainly will go to service
   */
  override def receive = super.receive orElse {
    case HttpGet(config, path, headers) => service(config, GET, path, None, headers)
    case HttpPost(config, path, body, headers) => service(config, POST, path, Some(body), headers)
    case HttpPut(config, path, body, headers) => service(config, PUT, path, Some(body), headers)
    case HttpDelete(config, path, headers) => service(config, DELETE, path, None, headers)
    case HttpOptions(config, path, body, headers) => service(config, OPTIONS, path, Some(body), headers)
    case HttpPatch(config, path, body, headers) => service(config, PATCH, path, Some(body), headers)
    case HttpPing(config, timeout, path) => pipe(ping(config, timeout, path)) to sender
  }

  /**
   * Will service the request by routing
   *
   * @param config
   * @param method
   * @param path
   * @param body
   * @param headers
   * @tparam T
   */
  // add marshallers based on the content-type in the headers
  def service[T:Manifest](config:HttpConfig, method:HttpMethod, path:String, body:Option[T], headers:List[HttpHeader]) = {
    implicit val HttpJsonMarshaller =
      Marshaller.of[T](ContentTypes.`application/json`) { (value, contentType, ctx) =>
        ctx.marshalTo(HttpEntity(contentType, compactRender(decompose(value))))
      }
    try {
      val fullPath = config.fullPath(path)
      val responseFuture = pipeline {
        method match {
          case GET =>
            Get(fullPath).withHeaders(headers)
          case POST =>
            Post(fullPath, body).withHeaders(headers)
          case PUT =>
            Put(fullPath, body).withHeaders(headers)
          case DELETE =>
            Delete(fullPath).withHeaders(headers)
          case OPTIONS =>
            Options(fullPath, body).withHeaders(headers)
          case PATCH =>
            Patch(fullPath, body).withHeaders(headers)
          case HEAD =>
            Head(fullPath).withHeaders(headers)
        }
      }

      val caller = sender
      responseFuture onComplete {
        case Success(s) => caller ! HttpResp(s.entity, s.headers, s.status)
        case Failure(f) =>
          caller ! Status.Failure(HttpClientException("URL[%s] : %s".format(fullPath, f.getMessage), f))
      }
    } catch {
      case NonFatal(e) =>
        sender ! Status.Failure(HttpClientException(e.getMessage, e))
        throw HttpClientException(e.getMessage, e)
    }
  }

  def shutdown = {
    val f = IO(UHttp).ask(Http.CloseAll)(2 seconds)
    Await.ready(f, 2 seconds)
  }

  def ping(config:HttpConfig, timeoutValue:Int=1, pingPath:String="ping") : Future[ConnectionStatus] = {
    implicit val timeout = Timeout(Duration(timeoutValue, "seconds"))
    val p = Promise[ConnectionStatus]()
    try {
      val f = (self ? HttpGet(config, pingPath))(timeout)
      f onComplete {
        case Failure(f) =>
          p success ConnectionStatus(false, "Could not connect to " + config.fullPath("ping") + " responded with exception " + f.getMessage)
        case Success(s) =>
          s.asInstanceOf[HttpResp].statusCode match {
            case s:StatusCodes.Success => p success ConnectionStatus(true, "Connected successfully to " + config.fullPath(pingPath))
            case _ => p success ConnectionStatus(false, "Could not connect to " + config.fullPath(pingPath))
          }
      }
    } catch {
      case e:Throwable =>
        log.debug("Could not connect to " + config.fullPath(pingPath) + " responded with error " + e.getMessage)
        p success ConnectionStatus(false, "Could not connect to " + config.fullPath(pingPath) + " responded with error " + e.getMessage)
    }
    p.future
  }
}
