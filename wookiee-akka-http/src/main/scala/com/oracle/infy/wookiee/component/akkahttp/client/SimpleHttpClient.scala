/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.client

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.Materializer
import akka.util.ByteString
import com.oracle.infy.wookiee.logging.LoggingAdapter

import scala.concurrent.{ExecutionContext, Future}

trait SimpleHttpClient extends LoggingAdapter { this: Actor =>

  implicit val executionContext: ExecutionContext
  implicit val materializer: Materializer = Materializer(context)

  private val http = Http(context.system)

  def request(req: HttpRequest): Future[(HttpResponse, Array[Byte])] = {
    http.singleRequest(req).flatMap { response =>
      response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(bs => (response, bs.toArray))
    }
  }

  def requestAs[T](req: HttpRequest, mapper: Array[Byte] => T): Future[(HttpResponse, T)] = {
    request(req).map {
      case (response, bytes) =>
        (response, mapper(bytes))
    }
  }

  def requestAsString(req: HttpRequest): Future[(HttpResponse, String)] = {
    requestAs[String](req, b => new String(b, "utf-8"))
  }

  def getPing(url: String): Future[Boolean] = {
    val response = requestAsString(HttpRequest(HttpMethods.GET, url))

    response.map {
      case (resp, body) if resp.status == StatusCodes.OK =>
        body.startsWith("pong")
      case (resp, body) =>
        log.error(s"Unexpected response from ping check with status ${resp.status}: $body")
        false
    }
  }
}
