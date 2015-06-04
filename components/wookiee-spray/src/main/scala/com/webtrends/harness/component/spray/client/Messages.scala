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

import akka.util.Timeout
import spray.http.CacheDirectives.`max-age`
import spray.http.HttpEncodings._
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.{HttpEntity, HttpHeader, StatusCode}

import scala.concurrent.duration.Duration

object HttpConstants {
  val defaultHeaders = List[HttpHeader](
    new Connection(Seq("keep-alive")),
    new `Cache-Control`(Seq(new `max-age`(0))),
    new Accept(Seq(`application/json`, `text/html`, `text/plain`, `application/xml`, `application/xhtml+xml`)),
    new `Accept-Encoding`(Seq(gzip, deflate))
  )
}

/**
 * Config for any http request
 *
 * @param timeout default 5, implicitly set
 * @param server server that you are posting the request too
 * @param port port for that server, defaults to 80
 * @param useSSL default false, if need ssl set to true
 */
class HttpConfig(server:String, port:Int=80, useSSL:Boolean=false, timeout:Int=5) {
  val pathString = "%s://%s:%d/%s"

  def fullPath(path:String) : String = {
    val protocol = if (useSSL) "https" else "http"
    pathString.format(protocol, server, port, path)
  }

  def getTimeout = Timeout(Duration(timeout, "seconds"))
}

case class ConnectionStatus(connect:Boolean, info:String)
case class HttpClientException(msg:String, t:Throwable) extends Exception(msg, t)

/**
 * Receiving messages for all Http request types GET, POST, PUT, DELETE, OPTIONS, PATCH
 * Also ping message that will use a GET to ping a specific server using path /ping (harness path) will return ConnectionStatus true for all success statusCodes
 */
case class HttpGet(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpPost[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpPut[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpDelete(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpOptions[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpPatch[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpHead(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)
case class HttpPing(config:HttpConfig, timeout:Int=1, path:String="ping")
// response message
case class HttpResp(resp:HttpEntity, headers:List[HttpHeader], statusCode:StatusCode)