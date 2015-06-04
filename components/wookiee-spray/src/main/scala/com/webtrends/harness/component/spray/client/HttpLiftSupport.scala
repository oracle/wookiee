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

import net.liftweb.json._
import spray.httpx.unmarshalling.Unmarshaller
import spray.http._
import spray.httpx.marshalling.Marshaller

trait HttpLiftSupport {
  implicit def httpFormats = DefaultFormats

  implicit def HttpJsonUnmarshaller[T: Manifest] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty =>
        parse(x.asString).extract[T]
    }

  implicit def HttpJsonMarshaller[T <: AnyRef] =
    Marshaller.of[T](ContentTypes.`application/json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(contentType, Serialization.write(value)))
    }
}
