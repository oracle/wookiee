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
package com.oracle.infy.wookiee.zookeeper

import com.oracle.infy.wookiee.Mediator
import com.typesafe.config.Config
import org.apache.curator.test.TestingServer

import java.util.concurrent.TimeUnit
import scala.util.Try

/**
  * @param dataCenter The data center to point to
  * @param pod The environment within the center
  * @param quorum The list of fqdn to the zookeeper quorum. Example: hzoo01.staging.dmz,hzoo02.staging.dmz,hzoo03.staging.dmz.
  * @param sessionTimeout The zookeeper session timeout. Defaults to 30 seconds.
  * @param connectionTimeout The allotted time to try an connect to zookeeper. Defaults to 30 seconds.
  * @param retrySleep The allotted time to sleep before trying to connect to zookeeper. Defaults to 5 seconds.
  * @param retryCount The number of times to retry to connect to zookeeper. Defaults to 150.
  */
case class ZookeeperSettings(
    dataCenter: String,
    pod: String,
    quorum: String,
    sessionTimeout: Long = 30000L,
    connectionTimeout: Long = 30000L,
    retrySleep: Long = 5000L,
    retryCount: Int = 150,
    basePath: String = ""
) {

  require(if (dataCenter.isEmpty) false else true, "Zookeeper datacenter MUST be set in the config")
  require(if (pod.isEmpty) false else true, "Zookeeper pod MUST be set in the config")
  require(if (quorum.isEmpty) false else true, "Zookeeper quorum MUST be set in the config")
  val version = "1.1"
}

object ZookeeperSettings extends Mediator[TestingServer] {
  val ComponentName = "wookiee-zookeeper"

  def isMock(config: Config): Boolean = {
    Try(config.getBoolean(ComponentName + ".mock-enabled")).getOrElse(false)
  }

  def getMockPort(config: Config): Option[Int] = {
    if (isMock(config)) Try(config.getInt(ComponentName + ".mock-port")).toOption
    else None
  }

  def apply(config: Config): ZookeeperSettings = {

    val conf =
      if (config.hasPath(ComponentName))
        config.getConfig(ComponentName).withFallback(config)
      else config
    // Set the quorum on the fly in any of our mock cases
    val quorum = if (isMock(config)) {
      getMockPort(config).flatMap(port => maybeGetMediator(port.toString)) match {
        case Some(mockZkServer) =>
          Try(conf getString "quorum") getOrElse
            mockZkServer.getConnectString
        case None =>
          getMockPort(config) match {
            case Some(port) => s"127.0.0.1:$port"
            case None       => throw new IllegalArgumentException("Zookeeper quorum MUST be set in the config")
          }
      }
    } else conf getString "quorum"

    ZookeeperSettings(
      conf getString "datacenter",
      conf getString "pod",
      quorum,
      conf.getDuration("session-timeout", TimeUnit.MILLISECONDS),
      conf.getDuration("connection-timeout", TimeUnit.MILLISECONDS),
      conf.getDuration("retry-sleep", TimeUnit.MILLISECONDS),
      conf getInt "retry-count",
      conf getString "base-path"
    )
  }
}
