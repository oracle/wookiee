/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.authentication

import java.net.{InetAddress, UnknownHostException}
import java.util
import akka.japi.Util._
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ConfigUtil
import com.typesafe.config.Config

/**
  * @author Michael Cuthbert on 2/23/15.
  */
case class CIDRRules(cidrAllow: Seq[String], cidrDeny: Seq[String]) extends LoggingAdapter {

  private lazy val allow = { cidrAllow map (x => new IpAddressMatcher(x.trim)) }
  private lazy val deny = { cidrDeny map (x => new IpAddressMatcher(x.trim)) }

  def checkCidrRules(ip: InetAddress): Boolean = {
    try {
      deny.count(_.matches(ip.getHostAddress)) match {
        case 0 if allow.nonEmpty =>
          // No denies, but we must match against allows
          allow.count(_.matches(ip.getHostAddress)) match {
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
      case iae: IllegalArgumentException =>
        log.info("CIDR rules do not support IPv6 yet", iae)
        false
    }
  }
}

object CIDRRules {

  def apply(config: Config): CIDRRules = {
    val c = ConfigUtil.prepareSubConfig(config, "cidr-rules")

    CIDRRules(
      immutableSeq(c getStringList "allow" toArray Array.empty[String]),
      immutableSeq(c getStringList "deny" toArray Array.empty[String])
    )
  }
}

final class IpAddressMatcher(var ipAddress: String) {

  private val nMaskBits: Int = if (ipAddress.indexOf(47) > 0) {
    val addressAndMask: Array[String] = ipAddress.split("/")
    ipAddress = addressAndMask(0)
    addressAndMask(1).toInt
  } else -1

  private val requiredAddress: InetAddress = parseAddress(ipAddress)

  def matches(address: String): Boolean = {
    val remoteAddress: InetAddress = parseAddress(address)
    if (!(requiredAddress.getClass == remoteAddress.getClass)) false
    else if (nMaskBits < 0) remoteAddress == requiredAddress
    else {
      val remAddr: Array[Byte] = remoteAddress.getAddress
      val reqAddr: Array[Byte] = requiredAddress.getAddress
      val oddBits: Int = nMaskBits % 8
      val nMaskBytes: Int = nMaskBits / 8 + (if (oddBits == 0) 0
                                             else 1)
      val mask: Array[Byte] = new Array[Byte](nMaskBytes)
      val topBits = if (oddBits == 0) {
        mask.length
      } else mask.length - 1

      util.Arrays.fill(mask, 0, topBits, -1.toByte)

      var i: Int = 0
      if (oddBits != 0) {
        i = (1 << oddBits) - 1
        i <<= 8 - oddBits
        mask(mask.length - 1) = i.toByte
      }
      i = 0
      while (i < mask.length) {
        if ((remAddr(i) & mask(i)) != (reqAddr(i) & mask(i)))
          return false
        i += 1
      }
      true
    }
  }

  private def parseAddress(address: String): InetAddress =
    try {
      InetAddress.getByName(address)
    } catch {
      case var3: UnknownHostException =>
        throw new IllegalArgumentException("Failed to parse address" + address, var3)
    }
}
