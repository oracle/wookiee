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

package com.webtrends.harness.component.socko.utils

import com.webtrends.harness.command.Command
import com.webtrends.harness.component.socko.route.{SockoHeader, EntityRoutes}
import org.mashupbots.socko.events.ImmutableHttpHeaders

trait CORSSupportDirectives extends EntityRoutes with Command {
  /**
   * Will add the CORS specific headers to the response if 'origin' is present on the request
   */
  override def getResponseHeaders(headers: ImmutableHttpHeaders): Map[String, List[SockoHeader]] = {
    headers.get("Origin") match {
      case None => super.getResponseHeaders(headers)
      case Some(s) =>
        Map(SockoUtils.KeyAllRequestMethods -> List(SockoHeader("Access-Control-Allow-Origin", if (s == "null") "*" else s), SockoHeader("Access-Control-Allow-Credentials", "true")))
    }
  }
}
