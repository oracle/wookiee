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

package com.oracle.infy.wookiee.component.zookeeper.mock

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.PoisonPill
import com.oracle.infy.wookiee.component.zookeeper.{ZookeeperActor, ZookeeperAdapter, ZookeeperService}
import com.oracle.infy.wookiee.zookeeper.ZookeeperSettings
import com.typesafe.config.Config

/**
  * Use this object to spin up a local zookeeper for unit testing.
  * Requires org.apache.curator:curator-test
  * To use ZK service from a test class execute this code before anything:
  * val zkServer = new TestingServer()
  * implicit val system = ActorSystem("SomeTest")
  * lazy val zkService = MockZookeeper(zkServer.getConnectString)
  *
  * Now you should be able to call ZK state changing methods in zkService (ZookeeperService.scala).
  * Note: The clusterEnabled flag exists to support wookiee-cluster
  */
object MockZookeeper {
  case class MockZookeeper(override val config: Config) extends ZookeeperAdapter

  // Only zkSettings is required
  def apply(zkSettings: ZookeeperSettings, clusterEnabled: Boolean = false)(
      implicit config: Config
  ): ZookeeperAdapter = {
    WookieeActor.actorOf(ZookeeperActor(zkSettings, clusterEnabled))
    MockZookeeper(config)
  }

  def apply(config: Config): ZookeeperAdapter =
    apply(if (config.hasPath("wookiee-zookeeper")) {
      ZookeeperSettings(config.getConfig("wookiee-zookeeper"))
    } else {
      ZookeeperSettings(config)
    })(config)

  def apply(zookeeperQuorum: String)(implicit config: Config): ZookeeperAdapter =
    apply(getTestConfig(zookeeperQuorum))

  def getTestConfig(config: Config): ZookeeperSettings =
    ZookeeperSettings(config)

  def getTestConfig(zookeeperQuorum: String)(implicit config: Config): ZookeeperSettings =
    if (zookeeperQuorum.nonEmpty) {
      ZookeeperSettings("test", "pod", zookeeperQuorum)
    } else {
      ZookeeperSettings(config)
    }

  def stop(implicit config: Config): Unit =
    ZookeeperService
      .maybeGetMediator(
        config
      )
      .foreach(_ ! PoisonPill)
}
