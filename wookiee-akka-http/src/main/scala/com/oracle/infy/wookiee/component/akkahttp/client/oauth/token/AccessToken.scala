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
import org.json4s.JsonAST.{JDouble, JInt, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, Formats, NoTypeHints}

import scala.concurrent.Future

case class AccessToken(
    access_token: String,
    token_type: String,
    scope: Option[String],
    expires_in: Int,
    refresh_token: Option[String]
)

object AccessToken {
  implicit val formats: Formats = Serialization.formats(NoTypeHints) + new NumberSerializer()

  implicit def um: FromEntityUnmarshaller[AccessToken] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypes.`application/json`).map { str =>
      parse(str).extract[AccessToken]
    }

  def apply(response: HttpResponse)(implicit mat: Materializer): Future[AccessToken] = {
    Unmarshal(response).to[AccessToken]
  }
}

class NumberSerializer
    extends CustomSerializer[Int](
      _ =>
        (
          {
            case JInt(x)    => x.toInt
            case JDouble(x) => x.toInt
            case JString(x) => x.toInt
          }, {
            case x: Int => JInt(x)
          }
        )
    )
