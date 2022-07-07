/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.infy.wookiee.component.memcache

import java.util.concurrent.TimeUnit

import com.twitter.finagle.Memcached
import com.twitter.finagle.memcached._
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Duration, Return, Throw, Future => TwitterFuture}
import com.oracle.infy.wookiee.component.cache.{Cache, CacheConfig}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import upickle.default._

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
  val caches: mutable.Map[String, Memcache] = mutable.Map[String, Memcache]()

  def addCache(name: String, client: Memcache): caches.type = {
    caches += (name -> client)
  }
}

@SerialVersionUID(11L)
case class CacheWrapper(expirationTime: Long, data: Array[Byte]) extends Serializable

class MemcacheManager(name: String) extends Cache(name) with MemcacheConstants {

  import context.dispatcher
  def caches: mutable.Map[String, Memcache] = MemcacheManager.caches

  override def postStop(): Unit = {
    // close all connections on stop
    caches map {
      case (_, v) => v.close
    }
    super.postStop()
  }

  /**
    * Function that will convert Twitter Futures to Scala Futures, so we can respond to the user correctly
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
  override protected def createCache(config: CacheConfig): Boolean = {
    caches.get(config.namespace) match {
      case Some(c) if c != null => true
      case Some(_)              => false
      case None =>
        val cache = Memcache(build(config), config)
        MemcacheManager.addCache(config.namespace, cache)
        true
    }
  }

  /**
    * Deletes the cache in memcache
    * Not supported by memcache
    */
  override protected def deleteCache(namespace: String): Boolean = {
    false
  }

  /**
    * Gets an element from a cache given the namespace
    *
    * @param namespace A namespace will reference a specific cache
    * @param key The key that you are looking up in the cache
    * @return an Option[ChannelBuffer] to the caller
    */
  override protected def get(namespace: String, key: String): Future[Option[Array[Byte]]] = {
    caches.get(namespace) match {
      case Some(c) =>
        fromTwitter(c.get(key)).map {
          case Some(wrapped) =>
            val wrapper = readBinary[CacheWrapper](Buf.ByteArray.Owned.extract(wrapped))(macroRW)
            if (wrapper.expirationTime == -1 || wrapper.expirationTime > System.currentTimeMillis()) {
              Some(wrapper.data)
            } else {
              delete(namespace, key)
              None
            }
          case None =>
            None
        }
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
  override protected def add(
      namespace: String,
      key: String,
      value: Array[Byte],
      ttlSec: Option[Int]
  ): Future[Boolean] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.set(key, wrapData(ttlSec, value)))
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
  override protected def delete(namespace: String, key: String): Future[Boolean] = {
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
  override protected def increment(namespace: String, key: String, incrementBy: Long): Future[Option[Long]] = {
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
  override protected def decrement(namespace: String, key: String, decrementBy: Long): Future[Option[Long]] = {
    caches.get(namespace) match {
      case Some(c) => fromTwitter(c.decrement(key, decrementBy))
      case None =>
        log.error("Failed to decrement key [%s] in cache [%s] as it does not exist".format(key, namespace))
        Future { None }
    }
  }

  // NOT IMPLEMENTED RETURN FALSE
  override protected def contains(namespace: String, key: String): Future[Boolean] = {
    Future { false }
  }

  // NOT SUPPORTED BY MEMCACHE
  override protected def clear(namespace: String): Future[Boolean] = {
    Future { false }
  }

  /**
    * This builds the memcache client.
    * In the future we will need to create more options for different types of clients, like Replication, Cluster and Ketama
    * Initially will just use SimpleClient
    */
  private def build(config: CacheConfig): Client = {
    val serverList = config.getProperty(KeyServerList, None).get.asInstanceOf[String]

    Memcached
      .client
      .withEjectFailedHost(false)
      .withRequestTimeout(Duration(15, TimeUnit.SECONDS))
      .withSession
      .acquisitionTimeout(Duration(15, TimeUnit.SECONDS))
      .newRichClient(serverList)
  }

  def wrapData(ttlSec: Option[Int], data: Array[Byte]): Buf = {
    val wrapper = CacheWrapper(ttlSec.map(_ * 1000 + System.currentTimeMillis()).getOrElse(-1L), data)
    val array = writeBinary(wrapper)(macroW0)
    new ByteArray(array, 0, array.length)
  }

  override def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()
    if (caches.isEmpty) {
      p success HealthComponent(self.path.name, ComponentState.NORMAL, "Managing %d caches".format(0))
    } else {
      val statuses = caches.map {
        case (_, v) => fromTwitter(v.checkHealth()).mapTo[CacheStatus]
      }
      Future.sequence(statuses) onComplete {
        case Success(result) =>
          val comp = HealthComponent(self.path.name, ComponentState.NORMAL, "Managing %d caches".format(result.size))
          result.zipWithIndex.foreach {
            case (status, i) =>
              status match {
                case CacheStatus(true, info) =>
                  comp.addComponent(
                    HealthComponent(self.path.name + " Cache Connection " + i, ComponentState.NORMAL, info)
                  )
                case CacheStatus(false, info) =>
                  comp.addComponent(
                    HealthComponent(self.path.name + " Cache Connection " + i, ComponentState.CRITICAL, info)
                  )
              }
          }
          p success comp
        case Failure(f) => p success HealthComponent(self.path.name, ComponentState.CRITICAL, f.getMessage)
      }
    }
    p.future
  }
}
