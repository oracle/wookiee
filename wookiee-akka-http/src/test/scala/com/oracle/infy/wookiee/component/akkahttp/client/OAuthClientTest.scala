/*
 *  Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.oracle.infy.wookiee.component.akkahttp.client.oauth._
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.config._
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.token.Error.{InvalidClient, UnauthorizedException, Unknown}
import com.oracle.infy.wookiee.component.akkahttp.client.oauth.token.{AccessToken, GrantType}
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class OAuthClientTest extends AsyncFlatSpec
  with Matchers
  with ScalatestRouteTest {

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), Duration.Inf)
    ()
  }

  behavior of "oauthClient"

  it should "#getAuthorizeUrl should delegate processing to strategy" in {
    import strategy._

    val config = Config("xxx", "yyy", site = Uri("https://example.com"), authorizeUrl = "/oauth/custom_authorize")
    val client = OAuthClient(config)
    val result = client.getAuthorizeUrl(GrantType.AuthorizationCode, Map("redirect_uri" -> "https://example.com/callback"))
    val actual = result.get.toString
    val expect = "https://example.com/oauth/custom_authorize?redirect_uri=https://example.com/callback&response_type=code&client_id=xxx"
    assert(actual == expect)
  }

  it should "#getAccessToken return Right[AccessToken] when oauth provider approves" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.OK,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "access_token": "xxx",
           |  "token_type": "bearer",
           |  "expires_in": 86400,
           |  "refresh_token": "yyy"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode, Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isRight)
    }
  }

  import strategy._

  def checkHeadersAndEntity[T <: GrantType](location: ClientLocation,
                            grant: T,
                            toCheck: (Seq[HttpHeader], String) => Assertion)
                           (implicit strategy: Strategy[T]): Future[Assertion] = {
    val cannedResponse = HttpResponse(
      status = StatusCodes.OK,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "access_token": "xxx",
           |  "token_type": "bearer",
           |  "expires_in": 86400,
           |  "refresh_token": "yyy"
           |}
         """.stripMargin
      )
    )

    var reqHeaders = Seq.empty[HttpHeader]
    var reqEntity: Option[HttpEntity.Strict] = None
    val mockConnection = Flow[HttpRequest].map { req =>
      reqHeaders = req.headers
      reqEntity = Some(Await.result(req.entity.toStrict(5.seconds), 6.seconds))
      cannedResponse
    }
    val config         = Config("xxx", "yyy", Uri("https://example.com"), clientLocation = location)
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(grant, Map("username" -> "goober", "password" -> "openup"))

    result.map { r =>
      assert(r.isRight)
      val entity = reqEntity.get.toString()
      toCheck(reqHeaders, entity)
    }
  }

  def check(headers: Seq[HttpHeader], entity: String): Assertion = {
    assert(headers.exists(_.lowercaseName() == "authorization"))
    assert(!entity.contains("client_id") && !entity.contains("client_secret"))
  }

  it should "Client Creds respects OnHeader" in {
    checkHeadersAndEntity(OnHeader, GrantType.ClientCredentials, check)
  }

  it should "Username Creds respects OnHeader" in {
    checkHeadersAndEntity(OnHeader, GrantType.PasswordCredentials, check)
  }

  it should "Client Creds respects OnBoth" in {
    checkHeadersAndEntity(OnBoth, GrantType.ClientCredentials, check)
  }

  it should "Username Creds respects OnBoth" in {
    checkHeadersAndEntity(OnBoth, GrantType.PasswordCredentials, check)
  }

  it should "parse json to access tokens even if expires_in is a string" in {
    val tokenJson =
        s"""
           |{
           |  "access_token": "xxx",
           |  "token_type": "bearer",
           |  "expires_in": "86400",
           |  "refresh_token": "yyy"
           |}
         """.stripMargin

    val resp = HttpResponse(StatusCodes.Accepted, List.empty[HttpHeader],
      HttpEntity.Strict(ContentTypes.`application/json`, ByteString(tokenJson)))
    val tokenFut = AccessToken(resp)

    tokenFut.map { token =>
      assert(token.expires_in == 86400)
    }
  }

  it should "return Left[UnauthorizedException] when oauth provider rejects" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.Unauthorized,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "error": "invalid_client",
           |  "error_description": "description"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode, Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isLeft)
      assert(r.left.exists(_.isInstanceOf[UnauthorizedException]))
      val exception = r.swap.getOrElse(new UnauthorizedException(
        Unknown, Some("test-failed"), HttpResponse())).asInstanceOf[UnauthorizedException]
      assert(exception.description.get == "description")
      assert(exception.code == InvalidClient)
    }
  }

  it should "not break when error_description is absent" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.Unauthorized,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "error": "invalid_client"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode,
      Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isLeft)
      assert(r.left.exists(_.isInstanceOf[UnauthorizedException]))
      val exception = r.swap.getOrElse(new UnauthorizedException(
        Unknown, Some("test-failed"), HttpResponse())).asInstanceOf[UnauthorizedException]
      assert(exception.description.isEmpty)
      assert(exception.code == InvalidClient)
    }
  }

  it should "#getConnectionWithAccessToken return outgoing connection flow with access token" in {
    val accessToken = AccessToken(
      access_token = "xxx",
      token_type = "bearer",
      expires_in = 86400,
      refresh_token = Some("yyy"),
      scope = None
    )

    val request = HttpRequest(HttpMethods.GET, "/v1/foo/bar")
    val response = HttpResponse(
      status = StatusCodes.OK,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "key": "value"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest]
      .filter { req =>
        req.headers.exists(_.is("authorization")) && req.headers.exists(_.value() == s"Bearer ${accessToken.access_token}")
      }
      .map(_ => response)

    val config = Config("xxx", "yyy", Uri("https://example.com"))
    val client = OAuthClient(config, mockConnection)
    val result = Source.single(request).via(client.getConnectionWithAccessToken(accessToken)).runWith(Sink.head)

    result.map { r =>
      assert(r.status.isSuccess())
    }
  }

  it should "construct schema and host correctly" in {
    val config         = Config("xxx", "yyy", Uri("https://example.com:8080"))
    assert(config.getSchemaAndHost == "https://example.com:8080")
  }
}
