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

package com.oracle.infy.wookiee.component.akkahttp.client.oauth

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.config.ConfigLike
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.strategy.Strategy
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.token.Error.UnauthorizedException
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.token.{AccessToken, GrantType}

import scala.concurrent.{ExecutionContext, Future}

class OAuthClient(config: ConfigLike, connection: Option[Flow[HttpRequest, HttpResponse, _]] = None)(
    implicit system: ActorSystem
) {

  def getAuthorizeUrl[A <: GrantType](grant: A, params: Map[String, String] = Map.empty)(
      implicit s: Strategy[A]
  ): Option[Uri] =
    s.getAuthorizeUrl(config, params)

  def getAccessToken[A <: GrantType](
      grant: A,
      params: Map[String, String] = Map.empty,
      headers: Map[String, String] = Map.empty
  )(implicit s: Strategy[A], ec: ExecutionContext, mat: Materializer): Future[Either[Throwable, AccessToken]] = {
    val source = s.getAccessTokenSource(config, params, headers)

    source
      .via(connection.getOrElse(defaultConnection))
      .mapAsync(1)(handleError)
      .mapAsync(1)(AccessToken.apply)
      .runWith(Sink.head)
      .map(Right.apply)
      .recover {
        case ex => Left(ex)
      }
  }

  def getConnectionWithAccessToken(accessToken: AccessToken): Flow[HttpRequest, HttpResponse, _] =
    Flow[HttpRequest]
      .map(_.addCredentials(OAuth2BearerToken(accessToken.access_token)))
      .via(connection.getOrElse(defaultConnection))

  private def defaultConnection: Flow[HttpRequest, HttpResponse, _] =
    config.site.scheme match {
      case "http"  => Http().outgoingConnection(config.getHost, config.getPort)
      case "https" => Http().outgoingConnectionHttps(config.getHost, config.getPort)
    }

  private def handleError(
      response: HttpResponse
  )(implicit ec: ExecutionContext, mat: Materializer): Future[HttpResponse] = {
    if (response.status.isFailure()) UnauthorizedException.fromHttpResponse(response).flatMap(Future.failed(_))
    else Future.successful(response)
  }
}

object OAuthClient {

  def apply(config: ConfigLike)(implicit system: ActorSystem): OAuthClient =
    new OAuthClient(config)

  def apply(config: ConfigLike, connection: Flow[HttpRequest, HttpResponse, _])(
      implicit system: ActorSystem
  ): OAuthClient =
    new OAuthClient(config, Some(connection))
}
