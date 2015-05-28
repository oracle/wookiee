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

package com.webtrends.harness.component.zookeeper

import akka.actor.{ActorRef, ActorSystem, Identify, Props}
import akka.pattern.ask
import com.typesafe.config.Config
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.service.test.config.TestConfig

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * woods
 * 1/7/15
 */
object TestZookeeperActor {

  def props(config: Config): Props = Props(classOf[TestZookeeperActor], ZookeeperSettings(config))
  def props(zookeeperQuorum: String)(implicit system: ActorSystem): Props = Props(classOf[TestZookeeperActor], getConfig(zookeeperQuorum))

  def apply(zookeeperQuorum: String)(implicit system: ActorSystem): ActorRef = {

    val zk = system.actorOf(Props(classOf[TestZookeeperActor], getConfig(zookeeperQuorum)), "zookeeper")
    // We will block until the actor is fully loaded. We do this because Curator takes
    // upwards of 15+ seconds to connect and will cause issues if we don't return back a fully loaded
    // actor reference.
    Await.result((zk ? Identify("xyz123"))(15 seconds), 15 seconds)
    zk
  }

  private def getConfig(zookeeperQuorum: String)(implicit system: ActorSystem): ZookeeperSettings = {
    if (!zookeeperQuorum.isEmpty) {
      val conf = "wookiee-zookeeper.quorum=\"" + zookeeperQuorum + "\""
      ZookeeperSettings(TestConfig.conf(conf))
    }
    if (system.settings.config.hasPath("wookiee-zookeeper")) {
      ZookeeperSettings(system.settings.config.getConfig("wookiee-zookeeper"))
    } else {
      ZookeeperSettings(system.settings.config)
    }
  }
}

class TestZookeeperActor(settings: ZookeeperSettings) extends ZookeeperActor(settings) {
  log.info(s"Create the TestZookeeperActor and attaching to Zookeeper at ${context.system.settings.config.getString("wookiee-zookeeper.quorum")}")
}
