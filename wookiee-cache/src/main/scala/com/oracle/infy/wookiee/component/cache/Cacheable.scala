/*
 * Copyright 2015 oracle (http://www.oracle.com)
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
package com.oracle.infy.wookiee.component.cache

import com.oracle.infy.wookiee.utils.Loan._
import com.typesafe.config.Config
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, NoTypeHints}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.charset.StandardCharsets
import scala.concurrent._
import scala.util.{Failure, Success}

/**
  * Trait to help with caching objects in the wookiee-cache
  * A cacheable object will be converted to JSON and then stored as Array[Byte] in the cache manager
  *
  * TODO: Need to implement timeout strategies. Currently it simply times out the object based on when it
  * was inserted. This can cause an object to constantly be updating the insert time and possibly never timeout.
  * Also need to implement a strategy where everything will time out every hour or something like that.
  */
trait Cacheable[T] extends Serializable {
  this: Serializable =>

  @transient implicit def jsonFormats: Formats = DefaultFormats.lossless + NoTypeHints ++ JodaTimeSerializers.all

  /**
    * Gets the ttl for the data in the cache, by default will be set to None which means it will never time out
    * The value is in milliseconds. The ttl logic will be up to the specific cache implementation to maintain.
    * This can be overridden on individual calls to write
    *
    * @return Optional milliseconds for ttl of data in cache
    */
  def dataTimeout: Option[Long] = None

  /**
    * The key used to cache the object
    *
    * @return
    */
  def key: String = ???

  /**
    * The namespace used for the object
    *
    * @return
    */
  def namespace: String = ???

  /**
    * Extract function allows cacheable object to have control how it extracts the byte data from the array
    * by default it will try and parse it as a JSON object and then extract it to the class type
    *
    * @param obj the array of bytes
    * @return
    */
  protected def extract(obj: Array[Byte])(implicit m: Manifest[T]): Option[T] = {
    Some(Serialization.read[T](new String(obj, StandardCharsets.UTF_8)))
  }

  /**
    * Convenience method that one can call (after overriding extract) to convert bytes to a Serializable class.
    * Be sure to also override getBytes and call serialToBytes.
    */
  protected def bytesToSerial(obj: Array[Byte]): Option[T] = {
    loan(new ByteArrayInputStream(obj)) to { ba =>
      loan(new ObjectInputStream(ba)) to { os =>
        Some(os.readObject().asInstanceOf[T])
      }
    }
  }

  /**
    * getBytes function allows cacheable object to have control over how it writes the data to memcache
    * By default it will use JSON to decompose then render the object from json to a string and
    * then simply call getBytes on the string
    */
  protected def getBytes: Array[Byte] = {
    Serialization.write(this).getBytes(StandardCharsets.UTF_8)
  }

  /**
    * Convenience method that one can call (after overriding getBytes) to convert a Serializable class
    * to bytes to store in memcache. Be sure to also override extract and call bytesToSerial.
    */
  protected def serialToBytes(obj: Serializable): Array[Byte] = {
    loan(new ByteArrayOutputStream()) to { bs =>
      loan(new ObjectOutputStream(bs)) to { os =>
        os.writeObject(obj)
      }
      bs.toByteArray
    }
  }

  // Need a config that has the 'instance-id' of this Wookiee instance
  def readFromCacheConfig(
      config: Config,
      cacheKey: Option[CacheKey] = None
  )(implicit executor: ExecutionContext, m: Manifest[T]): Future[Option[T]] =
    readFromCache(Cache.getMediator(config), cacheKey)(executor, m)

  /**
    * Looks in the supplied cache for the current object
    *
    * @param cacheRef This is a reference to the cache actor
    * @return
    */
  def readFromCache(
      cacheRef: Cache,
      cacheKey: Option[CacheKey] = None
  )(implicit executor: ExecutionContext, m: Manifest[T]): Future[Option[T]] = {
    val ck = getCacheKey(cacheKey)
    val p = Promise[Option[T]]()
    val future = (cacheRef ? Get(namespace, ck)).mapTo[Option[Array[Byte]]]
    future onComplete {
      case Success(Some(d)) => p success extract(d)
      case Success(None)    => p success None
      case Failure(f)       => p failure f
    }
    p.future
  }

  // Need a config that has the 'instance-id' of this Wookiee instance
  def writeInCacheConfig(
      config: Config,
      cacheKey: Option[CacheKey] = None,
      ttlSec: Option[Int] = dataTimeout.map(_.toInt / 1000)
  ): Future[Boolean] =
    writeInCache(Cache.getMediator(config), cacheKey, ttlSec)

  /**
    * Writes the current object to the supplied cache
    *
    * @param cacheRef This is a reference to the cache actor
    */
  def writeInCache(
      cacheRef: Cache,
      cacheKey: Option[CacheKey] = None,
      ttlSec: Option[Int] = dataTimeout.map(_.toInt / 1000)
  ): Future[Boolean] =
    (cacheRef ? Add(namespace, getCacheKey(cacheKey), this.getBytes, ttlSec)).mapTo[Boolean]

  /**
    * Deletes the current item from the cache
    *
    * @param cacheRef The is a reference to the cache actor
    * @param cacheKey Optional key, usually this is managed by the object itself
    * @param executor the executor
    * @return true if delete was successful
    */
  def deleteFromCache(
      cacheRef: Cache,
      cacheKey: Option[CacheKey] = None
  )(implicit executor: ExecutionContext): Future[Boolean] =
    (cacheRef ? Delete(namespace, getCacheKey(cacheKey))).mapTo[Boolean]

  def deleteFromCacheConfig(
      config: Config,
      cacheKey: Option[CacheKey] = None
  )(implicit executor: ExecutionContext): Future[Boolean] =
    deleteFromCache(Cache.getMediator(config), cacheKey)(executor)

  def deserialize(data: Array[Byte])(implicit m: Manifest[T]): Option[T] = {
    extract(data)
  }

  protected def getCacheKey(cacheKey: Option[CacheKey]): String = {
    cacheKey match {
      case Some(s) => s.toString()
      case None    => key
    }
  }
}
