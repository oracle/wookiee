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
import org.apache.curator.x.discovery.{ServiceInstance, ServiceProvider, ServiceDiscoveryBuilder, ServiceDiscovery}
import scala.collection.mutable
import scala.collection.JavaConversions._

private[zookeeper] class Curator(settings: ZookeeperSettings) extends LoggingAdapter {

  private[zookeeper] case class ProviderKey(basePath:String, key:String)

  // We manage an internal client since the CuratorFramework will not allow re-use of the client
  // after it has been closed
  private var internalClient: Option[CuratorFramework] = None
  def client = internalClient.get

  // list of all discovery services by basepath
  private val discoveries = mutable.Map[String, ServiceDiscovery[Void]]()
  // we manage an internal list of all discoverable provider
  private val providers = mutable.Map[ProviderKey, ServiceProvider[Void]]()

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
    internalClient match {
      case Some(c) if c.getState == CuratorFrameworkState.STARTED =>
        c.close()
        internalClient = None
      case _ =>
    }
    discoveries.foreach(x => x._2.close())
  }

  def discovery(basePath:String): ServiceDiscovery[Void] = {
    val bp = getBasePath(basePath)
    if (discoveries.contains(bp)) {
      discoveries(bp)
    } else {
      val discovery = ServiceDiscoveryBuilder.builder(classOf[Void])
        .client(client)
        .basePath(bp)
        .build()
      discovery.start()
      discoveries.put(bp, discovery)
      discovery
    }
  }

  def createServiceProvider(basePath:String, name:String) : ServiceProvider[Void] = {
    val bp = getBasePath(basePath)
    val key = ProviderKey(bp, name)
    if (providers.contains(key)) {
      providers(key)
    } else {
      val provider = discovery(bp).serviceProviderBuilder().serviceName(name).build()
      provider.start()
      providers.put(key, provider)
      provider
    }
  }

  def getServiceProviderDetails(name:Option[String]=None) : Map[ProviderKey, Iterable[ServiceInstance[Void]]] = {
    val filteredMap = name match {
      case Some(n) => providers.filter(k => k._1.equals(n))
      case None => providers
    }
    filteredMap.map {
      k => k._1 -> collectionAsScalaIterable(k._2.getAllInstances)
    }.toMap
  }

  def registerService(basePath:String, instance:ServiceInstance[Void]) = {
    val bp = getBasePath(basePath)
    discovery(bp).registerService(instance)
    // create a provider for the service if one has not already been created for it
    createServiceProvider(bp, instance.getName)
  }

  private def getBasePath(basePath:String) : String = s"/${settings.dataCenter}/${settings.pod}${basePath}"
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
