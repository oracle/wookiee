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

package com.oracle.infy.wookiee.component.akkahttp.client.oauth.config

import akka.http.scaladsl.model.{HttpMethod, HttpMethods, Uri}

case class Config(
    clientId: String,
    clientSecret: String,
    site: Uri,
    authorizeUrl: String = "/oauth/authorize",
    tokenUrl: String = "/oauth/token",
    tokenMethod: HttpMethod = HttpMethods.POST,
    clientLocation: ClientLocation = OnBoth
) extends ConfigLike {
  def getHost: String = site.authority.host.address()

  def getPort: Int = site.scheme match {
    case "http"  => if (site.authority.port == -1) 80 else site.authority.port
    case "https" => if (site.authority.port == -1) 443 else site.authority.port
  }

  override def getSchemaAndHost: String = {
    site.scheme + "://" + site.authority.toString()
  }
}
