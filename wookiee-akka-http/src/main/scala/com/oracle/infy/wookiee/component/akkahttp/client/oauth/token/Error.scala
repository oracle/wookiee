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

package com.oracle.infy.wookiee.component.akkahttp.client.oauth.token

import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.stream.Materializer
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.{ExecutionContext, Future}

object Error {
  sealed abstract class Code(val value: String)
  case object InvalidRequest extends Code("invalid_request")
  case object InvalidClient extends Code("invalid_client")
  case object InvalidToken extends Code("invalid_token")
  case object InvalidGrant extends Code("invalid_grant")
  case object InvalidScope extends Code("invalid_scope")
  case object UnsupportedGrantType extends Code("unsupported_grant_type")
  case object Unknown extends Code("unknown")

  object Code {

    def fromString(code: String): Code = code match {
      case "invalid_request"        => InvalidRequest
      case "invalid_client"         => InvalidClient
      case "invalid_token"          => InvalidToken
      case "invalid_grant"          => InvalidGrant
      case "invalid_scope"          => InvalidScope
      case "unsupported_grant_type" => UnsupportedGrantType
      case _                        => Unknown
    }
  }

  class UnauthorizedException(val code: Code, val description: Option[String], val response: HttpResponse)
      extends RuntimeException(s"$code${description.map(d => ": " + d).getOrElse("")}")

  object UnauthorizedException {
    case class UnauthorizedResponse(error: String, error_description: Option[String])

    implicit val formats: Formats = DefaultFormats

    implicit def um: FromEntityUnmarshaller[UnauthorizedResponse] =
      Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypes.`application/json`).map { str =>
        parse(str).extract[UnauthorizedResponse]
      }

    def fromHttpResponse(
        response: HttpResponse
    )(implicit ec: ExecutionContext, mat: Materializer): Future[UnauthorizedException] = {
      Unmarshal(response)
        .to[UnauthorizedResponse]
        .map { r =>
          new UnauthorizedException(Code.fromString(r.error), r.error_description, response)
        }
        .recover {
          case err: Throwable =>
            new UnauthorizedException(Code.fromString("unknown"), Some(err.getMessage), response)
        }
    }
  }
}
