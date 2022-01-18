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

sealed abstract class GrantType(val value: String)

object GrantType {
  case object AuthorizationCode extends GrantType("authorization_code")
  case object ClientCredentials extends GrantType("client_credentials")
  case object PasswordCredentials extends GrantType("password")
  case object Implicit extends GrantType("implicit")
  case object RefreshToken extends GrantType("refresh_token")
}
