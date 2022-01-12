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
package com.oracle.infy.wookiee.component.zookeeper

import akka.actor.ActorSystem
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.component.zookeeper.config.ZookeeperSettings
import com.oracle.infy.wookiee.component.zookeeper.mock.MockZookeeper
import com.oracle.infy.wookiee.utils.ActorWaitHelper
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.test.TestingServer

import java.util.concurrent.TimeUnit
import scala.util.Try

trait Zookeeper {
  this: HActor =>
  import Zookeeper._
  implicit val system: ActorSystem = context.system

  // Generally clusterEnabled is only used by wookiee-cluster, also mocking support built
  // into this method for when mock-enabled or mock-port are set in wookiee-zookeeper
  def startZookeeper(clusterEnabled: Boolean = isClusterEnabled): Unit = {
    // Load the zookeeper actor
    if (isMock(config)) {
      log.info("Zookeeper Mock Mode Enabled, Starting Local Test Server...")
      getMockPort(config) match {
        case Some(port) =>
          val testCurator = CuratorFrameworkFactory.newClient(s"127.0.0.1:$port", 5000, 5000, new RetryNTimes(3, 100))
          try {
            testCurator.start()
            testCurator.blockUntilConnected(2, TimeUnit.SECONDS)
            assert(testCurator.getZookeeperClient.isConnected)
          } catch {
            case _: Throwable =>
              log.info("^^^ Ignore above error if using multiple mock servers")
              mockZkServer = Some(new TestingServer(port))
          } finally {
            testCurator.close()
          }
        case None => mockZkServer = Some(new TestingServer())
      }

      MockZookeeper(zookeeperSettings, clusterEnabled)
    } else {
      // Start up ZK Actor as per normal, not mocking
      ActorWaitHelper.awaitActor(
        ZookeeperActor.props(zookeeperSettings, clusterEnabled),
        context.system,
        Some(Zookeeper.ZookeeperName)
      )
    }
    ()
  }

  def stopZookeeper(): Unit = {
    mockZkServer foreach {
      _.close()
    }
  }

  protected def zookeeperSettings: ZookeeperSettings = {
    if (isMock(config)) {
      if (getMockPort(config).isEmpty && mockZkServer.isEmpty)
        throw new IllegalStateException("Call startZookeeper() to create mockZkServer")
      val connect = getMockPort(config) match {
        case Some(port) if mockZkServer.isEmpty => s"127.0.0.1:$port"
        case _                                  => mockZkServer.get.getConnectString
      }
      val conf = ConfigFactory
        .parseString(
          ZookeeperManager.ComponentName +
            s""".quorum="$connect""""
        )
        .withFallback(config)
      ZookeeperSettings(conf)
    } else {
      ZookeeperSettings(config)
    }
  }

  protected def isClusterEnabled: Boolean = {
    Try(config.getBoolean("wookiee-cluster.enabled")).getOrElse(false)
  }
}

object Zookeeper {
  val ZookeeperName = "zookeeper"
  var mockZkServer: Option[TestingServer] = None

  def isMock(config: Config): Boolean = {
    Try(config.getBoolean(ZookeeperManager.ComponentName + ".mock-enabled")).getOrElse(false)
  }

  def getMockPort(config: Config): Option[Int] = {
    if (isMock(config)) Try(config.getInt(ZookeeperManager.ComponentName + ".mock-port")).toOption
    else None
  }
}
