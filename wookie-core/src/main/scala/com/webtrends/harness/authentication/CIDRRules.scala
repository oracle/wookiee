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

package com.webtrends.harness.authentication

import java.net.InetAddress

import akka.japi.Util._
import com.typesafe.config.Config
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.utils.ConfigUtil
import org.apache.commons.net.util.SubnetUtils

/**
 * @author Michael Cuthbert on 2/23/15.
 */
case class CIDRRules(cidrAllow:Seq[String], cidrDeny:Seq[String]) {

  private val log = Logger.getLogger(getClass)

  private lazy val allow = { cidrAllow map (x => new SubnetUtils(x.trim).getInfo) }
  private lazy val deny = { cidrDeny map (x => new SubnetUtils(x.trim).getInfo) }

  def checkCidrRules(ip:InetAddress) : Boolean = {
    try {
      deny.count(_.isInRange(ip.getHostAddress)) match {
        case 0 if allow.length > 0 =>
          // No denies, but we must match against allows
          allow.count(_.isInRange(ip.getHostAddress)) match {
            case 0 =>
              // Not explicitly denied, but no allow matches
              false
            case _ =>
              // Not denies and matches an allow
              true
          }
        case 0 =>
          // No denies and no allows to match against
          true
        case _ =>
          // Denied
          false
      }
    } catch {
      case iae:IllegalArgumentException =>
        log.info("CIDR rules do not support IPv6 yet", iae)
        false
    }
  }
}

object CIDRRules {
  def apply(config:Config) : CIDRRules = {
    val c = ConfigUtil.prepareSubConfig(config, "cidr-rules")

    CIDRRules(immutableSeq(c getStringList ("allow") toArray (Array.empty[String])),
              immutableSeq(c getStringList ("deny") toArray (Array.empty[String])))
  }
}
