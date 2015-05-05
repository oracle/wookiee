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

package com.webtrends.harness.component.socko.client

import akka.util.Timeout

import scala.concurrent.duration.Duration

/**
 * @author Michael Cuthbert on 1/30/15.
 */
object HttpConstants {
  val defaultHeaders = Map[String, String](
    "Connection" -> "keep-alive",
    "Cache-Control" -> "",
    "Accept" -> "application/json,text/html,text/plain,application/xml,application/xhtml+xml",
    "Accept-Encoding" -> "gzip,deflate"
  )

  val POST = "POST"
  val GET = "GET"
  val PUT = "PUT"
  val DELETE = "DELETE"
  val OPTIONS = "OPTIONS"
  val PATCH = "PATCH"
  val HEAD = "HEAD"
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
case class HttpGet(config:HttpConfig, path:String, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpPost[T](config:HttpConfig, path:String, body:T, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpPut[T](config:HttpConfig, path:String, body:T, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpDelete(config:HttpConfig, path:String, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpOptions[T](config:HttpConfig, path:String, body:T, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpPatch[T](config:HttpConfig, path:String, body:T, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpHead(config:HttpConfig, path:String, headers:Map[String, String]=HttpConstants.defaultHeaders)
case class HttpPing(config:HttpConfig, timeout:Int=1, path:String="ping")
// response message
case class HttpResp(resp:Array[Byte], headers:Map[String, String], statusCode:Int)