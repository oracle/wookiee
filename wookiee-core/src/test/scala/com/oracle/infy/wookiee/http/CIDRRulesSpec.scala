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
package com.oracle.infy.wookiee.http

import com.oracle.infy.wookiee.authentication.CIDRRules
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.InetAddress

class CIDRRulesSpec extends AnyWordSpecLike with Matchers {
  "CIDRRules " should {
    "allow ipv4 requests through localhost" in {
      val rules = CIDRRules(Seq("127.0.0.1/30"), Seq())
      rules.checkCidrRules(InetAddress.getByName("localhost")) equals true
    }

    "allow ipv4 requests through" in {
      val rules = CIDRRules(Seq("127.0.0.1/30"), Seq())
      rules.checkCidrRules(InetAddress.getByName("127.0.0.1")) equals true
    }

    "deny ipv4 requests on wrong address" in {
      val rules = CIDRRules(Seq("127.0.0.1/30"), Seq())
      rules.checkCidrRules(InetAddress.getByName("15.12.13.12")) equals false
    }

    "deny ipv4 requests in the deny subnet" in {
      val rules = CIDRRules(Seq("127.0.0.1/30"), Seq("127.10.0.1/30"))
      rules.checkCidrRules(InetAddress.getByName("127.10.0.1")) equals false
    }

    "allow ipv6 requests through" in {
      val rules = CIDRRules(Seq("1:0:0:0:0:0:0:1"), Seq())
      rules.checkCidrRules(InetAddress.getByName("1:0:0:0:0:0:0:1")) equals true
    }

    "deny ipv6 requests in the deny subnet" in {
      val rules = CIDRRules(Seq("1:0:0:0:0:0:0:1"), Seq("1:0:0:0:0:5:0:1"))
      rules.checkCidrRules(InetAddress.getByName("1:0:0:0:0:5:0:1")) equals false
    }
  }
}
