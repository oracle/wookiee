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

import akka.actor.{Actor, Identify}
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings

import scala.concurrent.Await
import scala.concurrent.duration._

trait Zookeeper {
  this: Actor=>

  import context.system

  implicit val zookeeperSettings:ZookeeperSettings

  def startZookeeper(clusterEnabled:Boolean=false) = {
    // Load the zookeeper actor
    val zk = context.actorOf(ZookeeperActor.props(zookeeperSettings, clusterEnabled), Zookeeper.ZookeeperName)

    // We need to block here during startup so that we know that things
    // are up and running for other services that depend on this
    implicit val to = Timeout(20 seconds)
    Await.result(zk ? Identify("StartZK"), to.duration)
  }
}

object Zookeeper {
  val ZookeeperName = "zookeeper"
}
