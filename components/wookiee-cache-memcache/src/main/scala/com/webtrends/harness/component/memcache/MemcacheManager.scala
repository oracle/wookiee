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
package com.webtrends.harness.component.memcache

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.memcached._
import com.twitter.finagle.memcached.protocol.text.Memcached
import com.twitter.util.{Duration, Return, Throw, Future => TwitterFuture}
import com.webtrends.harness.component.cache.{CacheConfig, Cache}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import org.jboss.netty.buffer.ChannelBuffer

import scala.collection.mutable
import scala.concurrent._
import scala.util.{Failure, Success}

/**
 * MemcacheManager will allow users to send messages to create caches, get, and various other methods
 * against Memcache. This code uses the Finagle library, so it's Futures and Promises are Finagle Futures and Promises
 * which is not the same as Scala Future and Promises, although they work pretty much the same way.
 */
object MemcacheManager {
  val ComponentName = "wookiee-cache-memcache"
  val caches = mutable.Map[String, Memcache]()

  def addCache(name:String, client:Memcache) = {
    caches += (name -> client)
  }
}

class MemcacheManager(name:String) extends Cache(name) with MemcacheConstants {

  import context.dispatcher
  def caches = MemcacheManager.caches

  override def postStop(): Unit = {
    // close all connections on stop
    caches map {
      case (k, v) => v.close
    }
    super.postStop()
  }

  /**
   * Function that will convert Twitter Futures to Scala Futures, so we can respond to the user correctly
   *
   * @param twitterFuture
   * @tparam A
   * @return
   */
  def fromTwitter[A](twitterFuture: TwitterFuture[A]): Future[A] = {
    val promise = Promise[A]()
    twitterFuture respond {
      case Return(a) => promise success a
      case Throw(e)  => promise failure e
    }
    promise.future
  }

  /**
   * Creates a cache with the given configuration
   *
   * @param config Configuration including namespace and setKey
   * @return
   */
  override protected def createCache(config:CacheConfig) : Boolean = {
    caches.get(config.namespace) match {
      case Some(c) =>
        if (c == null)
          false
        else
          true
      case None =>
        val cache = new Memcache(build(config), config)
        MemcacheManager.addCache(config.namespace, cache)
        true
    }
  }

  /**
   * Deletes the cache in memcache
   * Not supported by memcache
   *
   * @param namespace
   * @return
   */
  override protected def deleteCache(namespace:String) : Boolean = {
    false
  }

  /**
   * Gets an element from a cache given the namespace
   *
   * @param namespace A namespace will reference a specific cache
   * @param key The key that you are looking up in the cache
   * @return an Option[ChannelBuffer] to the caller
   */
  override protected def get(namespace:String, key:String) : Future[Option[ChannelBuffer]] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.get(key))
      case None =>
        log.error("Failed to get key [%s] from cache [%s] as it does not exist".format(key, namespace))
        Future { None }
    }
  }

  /**
   * Adds an element to a cache
   *
   * @param namespace The namespace for the specific cache that you want to get the element from
   * @param key The key for the value you are putting in the cache
   * @param value The value in the form of ChannelBuffer
   * @return an Option[Boolean] to the caller
   */
  override protected def add(namespace:String, key:String, value:ChannelBuffer) : Future[Boolean] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.set(key, value))
      case None =>
        log.error("Failed to add key [%s] to cache [%s] as it does not exist".format(key, namespace))
        Future { false }
    }
  }

  /**
   * Deletes a key from a cache.
   *
   * @param namespace The namespace of the cache that the user is looking into
   * @param key The key to delete
   * @return an Option[Boolean] to the caller
   */
  override protected def delete(namespace:String, key:String) : Future[Boolean] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.delete(key))
      case None =>
        log.error("Failed to delete key [%s] from cache [%s] as it does not exist".format(key, namespace))
        Future { false }
    }
  }

  /**
   * Increments the key value by the supplied incrementBy value
   *
   * @param namespace The namespace for the cache
   * @param key The key for incrementing the value
   * @param incrementBy the long value to increment by
   * @return an Option[Long] to the caller
   */
  override protected def increment(namespace:String, key:String, incrementBy:Long) : Future[Option[Long]] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.increment(key, incrementBy))
      case None =>
        log.error("Failed to increment key [%s] in cache [%s] as it does not exist".format(key, namespace))
        Future { None }
    }
  }

  /**
   * Decrements the key value by the supplied decrementBy value
   *
   * @param namespace The namespace for the cache
   * @param key The key for the decrementing the value
   * @param decrementBy an Option[Long] to the caller
   */
  override protected def decrement(namespace:String, key:String, decrementBy:Long) : Future[Option[Long]] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.decrement(key, decrementBy))
      case None =>
        log.error("Failed to decrement key [%s] in cache [%s] as it does not exist".format(key, namespace))
        Future { None }
    }
  }

  // NOT IMPLEMENTED RETURN FALSE
  override protected def contains(namespace: String, key: String) : Future[Boolean] = {
    Future { false }
  }

  // NOT SUPPORTED BY MEMCACHE
  override protected def clear(namespace: String) : Future[Boolean] = {
    Future { false }
  }

  /**
   * This builds the memcache client.
   * In the future we will need to create more options for different types of clients, like Replication, Cluster and Ketama
   * Initially will just use SimpleClient
   *
   * TODO: add timeouts for the connections
   *
   * @param config
   * @return
   */
  private def build(config:CacheConfig) : Client = {
    val serverList = config.getProperty(KeyServerList, None).get.asInstanceOf[String]
    val concurrency = config.getProperty(KeyConcurrency, Some(3)).get.asInstanceOf[Int]
    val timeout = config.getProperty(KeyTimeout, Some(1)).get.asInstanceOf[Int]

    KetamaClientBuilder()
      .clientBuilder(ClientBuilder().hostConnectionLimit(concurrency).codec(Memcached()).failFast(false))
      .failureAccrualParams(Int.MaxValue, Duration.Top)
      .dest(serverList)
      .build()
      .asInstanceOf[PartitionedClient]
  }

  // todo check connection to memcache
  override def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()
    if (caches.isEmpty) {
      p success HealthComponent(self.path.name, ComponentState.NORMAL, "Managing %d caches".format(0))
    } else {
      val future = Future.traverse(caches) {
        case (k, v) => fromTwitter(v.checkHealth())
      }.mapTo[Seq[CacheStatus]]
      future onComplete {
        case Success(result) =>
          val comp = HealthComponent(self.path.name, ComponentState.NORMAL, "Managing %d caches".format(result.size))
          result.zipWithIndex.foreach { case (status, i) =>
            status match {
              case CacheStatus(true, info) => comp.addComponent(HealthComponent(self.path.name + " Cache Connection " + i, ComponentState.NORMAL, info))
              case CacheStatus(false, info) => comp.addComponent(HealthComponent(self.path.name + " Cache Connection " + i, ComponentState.CRITICAL, info))
            }
          }
          p success comp
        case Failure(f) => p success HealthComponent(self.path.name, ComponentState.CRITICAL, f.getMessage)
      }
    }
    p.future
  }
}
