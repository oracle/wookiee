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

import com.oracle.infy.wookiee.component.zookeeper.config.ZookeeperSettings
import com.oracle.infy.wookiee.logging.LoggingAdapter
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.state.ConnectionStateListener
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.x.discovery.details.JsonInstanceSerializer
import org.apache.curator.x.discovery.{ServiceDiscovery, ServiceDiscoveryBuilder, ServiceInstance, ServiceProvider}

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.util.Try

private[zookeeper] class Curator(settings: ZookeeperSettings) extends LoggingAdapter {

  private[zookeeper] case class DiscoveryKey(basePath: String, key: String)
  private[zookeeper] case class ProviderKey(basePath: String, key: String)

  // We manage an internal client since the CuratorFramework will not allow re-use of the client
  // after it has been closed
  private var internalClient: Option[CuratorFramework] = None
  def client: CuratorFramework = internalClient.get

  // list of all discovery services by basepath
  private val discoveries = mutable.Map[DiscoveryKey, ServiceDiscovery[WookieeServiceDetails]]()
  // we manage an internal list of all discoverable provider
  private val providers = mutable.Map[ProviderKey, ServiceProvider[WookieeServiceDetails]]()

  private[zookeeper] def createClient: CuratorFramework = {
    if (internalClient.isEmpty) {
      internalClient = Some(
        CuratorFrameworkFactory.newClient(
          settings.quorum,
          settings.sessionTimeout.intValue,
          settings.connectionTimeout.intValue,
          new RetryNTimes(settings.retryCount, settings.retrySleep.intValue)
        )
      )
    }

    internalClient.get
  }

  private[zookeeper] def start(listener: Option[ConnectionStateListener] = None): Unit = internalClient.synchronized {
    if (internalClient.isEmpty) {
      createClient
    }

    if (client.getState == CuratorFrameworkState.LATENT) {
      log.info("Starting curator with quorum " + settings.quorum)
      if (listener.isDefined) {
        client.getConnectionStateListenable.addListener(listener.get)
      }

      client.start()
      client.getZookeeperClient.blockUntilConnectedOrTimedOut
    }
    ()
  }

  private[zookeeper] def stop(): Unit = internalClient.synchronized {
    log.debug("Stopping curator")
    internalClient match {
      case Some(c) if c.getState == CuratorFrameworkState.STARTED =>
        c.close()
        internalClient = None
      case _ =>
    }
    discoveries.foreach(x => Try(x._2.close()))
  }

  def discovery(
      basePath: String,
      service: Option[ServiceInstance[WookieeServiceDetails]] = None
  ): ServiceDiscovery[WookieeServiceDetails] = {
    internalClient.synchronized {
      val key = DiscoveryKey(basePath, service match {
        case Some(s) => s.getName
        case _       => ""
      })
      if (discoveries.contains(key)) {
        discoveries(key)
      } else {
        val discovery = ServiceDiscoveryBuilder
          .builder(classOf[WookieeServiceDetails])
          .client(client)
          .serializer(new JsonInstanceSerializer[WookieeServiceDetails](classOf[WookieeServiceDetails]))
          .basePath(basePath)
          .build()
        service.foreach(it => discovery.registerService(it))
        discovery.start()
        discoveries.put(key, discovery)
        discovery
      }
    }
  }

  def discovery(basePath: String, key: String): Option[ServiceDiscovery[WookieeServiceDetails]] = {
    discoveries.get(DiscoveryKey(basePath, key))
  }

  def createServiceProvider(basePath: String, name: String): ServiceProvider[WookieeServiceDetails] = {
    internalClient.synchronized {
      val key = ProviderKey(basePath, name)
      if (providers.contains(key)) {
        providers(key)
      } else {
        val provider = discovery(basePath)
          .serviceProviderBuilder()
          .serviceName(name)
          .providerStrategy(new WookieeWeightedStrategy())
          .build()
        provider.start()
        providers.put(key, provider)
        provider
      }
    }
  }

  def getServiceProviderDetails(
      name: Option[String] = None
  ): Map[ProviderKey, Iterable[ServiceInstance[WookieeServiceDetails]]] = {
    internalClient.synchronized {
      val filteredMap = name match {
        case Some(n) => providers.filter(k => k._1.toString.equals(n))
        case None    => providers
      }
      filteredMap.map { k =>
        k._1 -> k._2.getAllInstances.asScala
      }.toMap
    }
  }

  def registerService(basePath: String, instance: ServiceInstance[WookieeServiceDetails]): Unit = {
    internalClient.synchronized {
      // create a provider for the service if one has not already been created for it
      val key = ProviderKey(basePath, instance.getName)
      if (!providers.contains(key)) {
        val provider = discovery(basePath, Some(instance))
          .serviceProviderBuilder()
          .providerStrategy(new WookieeWeightedStrategy())
          .serviceName(instance.getName)
          .build()
        provider.start()
        providers.put(key, provider)
      }
      ()
    }
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
    *
    * @return the singleton instance of the Curator
    */
  def apply(settings: ZookeeperSettings): Curator = {
    curators.synchronized {
      curators.getOrElseUpdate(settings.quorum, new Curator(settings))
    }
  }

}
