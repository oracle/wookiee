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

import java.net.InetAddress
import java.nio.charset.Charset

import akka.actor.Actor
import akka.util.Timeout
import org.apache.curator.framework.CuratorFramework
import com.typesafe.config.Config
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.logging.ActorLoggingAdapter
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import org.apache.zookeeper.KeeperException.NoNodeException

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object NodeRegistration {

  /**
   * Get the node/cluster base path
   * @param config the system config
   * @return the base path
   */
  def getBasePath(config: Config): String = {
    val zookeeperSettings = ZookeeperSettings(config.getConfig("wookiee-zookeeper"))
    val basePath = if (config.hasPath("wookiee-cluster.base-path")) {
      config.getConfig("wookiee-cluster").getString("base-path")
    } else {
      zookeeperSettings.basePath
    }
    s"$basePath/${zookeeperSettings.dataCenter}_${zookeeperSettings.pod}/${zookeeperSettings.version}"
  }
}

trait NodeRegistration extends ZookeeperAdapter {
  this: Actor with ActorLoggingAdapter =>

  implicit val timeout = Timeout(5 seconds)

  import context.dispatcher

  val utf8 = Charset.forName("UTF-8")
  val address = SystemExtension(context.system).address
  val port = if (address.port.isDefined) address.port.get else context.system.settings.config.getInt("akka.remote.netty.tcp.port")


  private def getAddress: String = {

    val host = if (address.host.isEmpty || address.host.get.equalsIgnoreCase("localhost") || address.host.get.equals("127.0.0.1")) {
      InetAddress.getLocalHost.getCanonicalHostName
    } else {
      address.host.get
    }

    s"${host}:${port}"
  }

  def unregisterNode(curator: CuratorFramework, zookeeperSettings: ZookeeperSettings) = {

    val path = s"${NodeRegistration.getBasePath(context.system.settings.config)}/nodes/${getAddress}"
    Try({
      // Call Curator directly because this method is usually called after the actor's queue has been disabled
      curator.delete.forPath(path)
    }).recover({
      case e: NoNodeException =>
      // do nothing
      case e: Throwable =>
        log.warn(e, "The node {} could not be deleted", path)
    })
  }

  def registerNode(curator: CuratorFramework, zookeeperSettings: ZookeeperSettings, clusterEnabled: Boolean) {

    val add = getAddress
    val path = s"${NodeRegistration.getBasePath(context.system.settings.config)}/nodes/${add}"

    // Delete the node first
    deleteNode(path) onComplete {
      case Success(_) => log.debug("The node {} was deleted", path)
      case Failure(t) => log.error(t, "The node {} could not be deleted", path)
    }

    log.info("Registering harness to path: " + path)
    val json = compact(render(("address" -> add.toString) ~ ("cluster-enabled" -> clusterEnabled)))

    createNode(path, true, Some(json.getBytes(utf8))) onComplete {
      case Success(_) => log.debug("The node {} was created", path)
      case Failure(t) => log.error(t, "Failed to create node registration for {}", path)
    }
  }
}
