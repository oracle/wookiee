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
package com.webtrends.harness.component.spray.directive

import com.webtrends.harness.authentication.CIDRRules
import org.slf4j.LoggerFactory
import spray.http.HttpHeaders._
import spray.http.StatusCodes
import spray.routing.Directive0

trait CIDRDirectives extends BaseDirectives {

  val externalLogger = LoggerFactory.getLogger(this.getClass)
  implicit var cidrRules: Option[CIDRRules]

  def cidrFilter: Directive0 = {
    optionalHeaderValuePF {
      case `Remote-Address`(ip) => ip
    } flatMap {
      case Some(ip) if ip.toOption.isDefined =>
        cidrRules match {
          case Some(s) =>
            if (s.checkCidrRules(ip.toOption.get)) {
              pass
            } else {
              complete(StatusCodes.NotFound)
            }
          case None =>
            // if settings not found then we only allow local/loopback address
            if (ip.toOption.get.isLoopbackAddress || ip.toOption.get.isAnyLocalAddress) {
              pass
            } else {
              complete(StatusCodes.NotFound)
            }
        }
      case _ =>
        // Denied because there is no remote address header that has been injected
        complete(StatusCodes.NotFound)
    }
  }

}
