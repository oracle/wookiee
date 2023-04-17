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

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.{ZookeeperActor, ZookeeperAdapterNonActor, ZookeeperService}
import com.oracle.infy.wookiee.utils.ActorWaitHelper
import com.oracle.infy.wookiee.zookeeper.ZookeeperSettings
import com.typesafe.config.Config

import scala.concurrent.duration._

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
  case class MockZookeeper(override val zkActorSystem: ActorSystem) extends ZookeeperAdapterNonActor

  private[oracle] def props(settings: ZookeeperSettings, clusterEnabled: Boolean = false)(
      implicit system: ActorSystem
  ): Props =
    Props(classOf[ZookeeperActor], settings, clusterEnabled)

  // Only zkSettings is required
  def apply(zkSettings: ZookeeperSettings, clusterEnabled: Boolean = false, actorName: Option[String] = None)(
      implicit system: ActorSystem
  ): ZookeeperAdapterNonActor = {
    val zkActor = if (actorName.isDefined) {
      system.actorOf(props(zkSettings, clusterEnabled), actorName.get)
    } else system.actorOf(props(zkSettings, clusterEnabled))

    ActorWaitHelper.awaitActorRef(zkActor, system)(Timeout(15.seconds))
    MockZookeeper(system)
  }

  def apply(config: Config)(implicit system: ActorSystem): ZookeeperAdapterNonActor = {
    apply(if (system.settings.config.hasPath("wookiee-zookeeper")) {
      ZookeeperSettings(system.settings.config.getConfig("wookiee-zookeeper"))
    } else {
      ZookeeperSettings(system.settings.config)
    })
  }

  def apply(zookeeperQuorum: String)(implicit system: ActorSystem): ZookeeperAdapterNonActor = {
    apply(getTestConfig(zookeeperQuorum))
  }

  def getTestConfig(config: Config)(implicit system: ActorSystem): ZookeeperSettings = {
    ZookeeperSettings(config)
  }

  def getTestConfig(zookeeperQuorum: String)(implicit system: ActorSystem): ZookeeperSettings = {
    if (zookeeperQuorum.nonEmpty) {
      ZookeeperSettings("test", "pod", zookeeperQuorum)
    } else {
      ZookeeperSettings(system.settings.config)
    }
  }

  def stop(implicit system: ActorSystem): Unit = {
    ZookeeperService.getZkActor.foreach(_ ! PoisonPill)
  }
}
