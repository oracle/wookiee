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

import akka.pattern._
import com.oracle.infy.wookiee.component.Component
import scala.concurrent.Future

// Messages for Caching
case class CreateCache(config: CacheConfig)
case class Get(namespace: String, key: String)
case class Add(namespace: String, key: String, value: Array[Byte], ttlSec: Option[Int])
case class Delete(namespace: String, key: String)
case class DeleteCache(namespace: String)
case class Decrement(namespace: String, key: String, decrementBy: Long = 1L)
case class Increment(namespace: String, key: String, incrementBy: Long = 1L)
case class Contains(namespace: String, key: String)
case class Clear(namespace: String)

/**
  * for components, so this would most likely at some point need to be rebuilt
  *
  * Interface trait that handles the messaging for any caching classes. Then the user would just need to implement
  * the internal functions
  */
abstract class Cache(name: String) extends Component(name) {

  import context.dispatcher

  override def receive: Receive = super.receive orElse {
    case CreateCache(config)                    => sender() ! createCache(config)
    case DeleteCache(namespace)                 => sender() ! deleteCache(namespace)
    case Get(namespace, key)                    => pipe(get(namespace, key)) to sender(); ()
    case Add(namespace, key, value, ttlSec)     => pipe(add(namespace, key, value, ttlSec)) to sender(); ()
    case Delete(namespace, key)                 => pipe(delete(namespace, key)) to sender(); ()
    case Decrement(namespace, key, decrementBy) => pipe(decrement(namespace, key, decrementBy)) to sender(); ()
    case Increment(namespace, key, incrementBy) => pipe(increment(namespace, key, incrementBy)) to sender(); ()
    case Contains(namespace, key)               => pipe(contains(namespace, key)) to sender(); ()
    case Clear(namespace)                       => pipe(clear(namespace)) to sender(); ()
  }

  // Functions to implement for any cache managers using the ICache interface
  /**
    * Creates a cache based on the implementer of the function. So could be any cache type
    */
  protected def createCache(config: CacheConfig): Boolean
  protected def deleteCache(namespace: String): Boolean
  protected def get(namespace: String, key: String): Future[Option[Array[Byte]]]
  protected def add(namespace: String, key: String, value: Array[Byte], ttlSec: Option[Int]): Future[Boolean]
  protected def delete(namespace: String, key: String): Future[Boolean]
  protected def increment(namespace: String, key: String, incrementBy: Long): Future[Option[Long]]
  protected def decrement(namespace: String, key: String, decrementBy: Long): Future[Option[Long]]
  protected def contains(namespace: String, key: String): Future[Boolean]
  protected def clear(namespace: String): Future[Boolean]
}

object Cache {
  val ComponentName = "wookiee-cache"
}
