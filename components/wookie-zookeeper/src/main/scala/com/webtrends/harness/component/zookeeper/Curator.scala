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

import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.state.ConnectionStateListener
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryNTimes
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.logging.LoggingAdapter
import scala.collection.mutable

private[zookeeper] class Curator(settings: ZookeeperSettings) extends LoggingAdapter {

  // We manage an internal client since the CuratorFramework will not allow re-use of the client
  // after it has been closed
  private var internalClient: Option[CuratorFramework] = None

  def client = internalClient.get

  private[zookeeper] def createClient: CuratorFramework = {
    if (internalClient.isEmpty) {
    internalClient = Some(CuratorFrameworkFactory.newClient(
      settings.quorum,
      settings.sessionTimeout.intValue,
      settings.connectionTimeout.intValue, new RetryNTimes(
        settings.retryCount,
        settings.retrySleep.intValue)))

    }

    internalClient.get
  }

  private[zookeeper] def start(listener: Option[ConnectionStateListener] = None): Unit = {
    if (internalClient.isEmpty) {
      createClient
    }

    client.synchronized {
      if (client.getState == CuratorFrameworkState.LATENT) {
        log.info("Starting curator with quorum " + settings.quorum)
        if (listener.isDefined) {
          client.getConnectionStateListenable.addListener(listener.get)
        }

        client.start
        client.getZookeeperClient.blockUntilConnectedOrTimedOut
      }
    }
  }

  private[zookeeper] def stop: Unit = {
    log.debug("Stopping curator")
    client.close
    internalClient = None
  }
}

/**
 * This allows access to a single instance of the curator client per quorum when an application
 * needs direct access. It is still preferred for one to use the ZookeeperAdapter
 * and ZookeeperEventAdapter traits.
 */
object Curator {

  private val curators = mutable.Map.empty[String, Curator]

  /**
   * Return the instance of the Curator client object. This is usually used after the harness has
   * called the apply method that takes the required settings.
   * @return the singleton instance of the Curator
   */
  def apply(settings: ZookeeperSettings): Curator = {
    curators.synchronized {
      curators.getOrElseUpdate(settings.quorum, new Curator(settings))
    }
  }

}
