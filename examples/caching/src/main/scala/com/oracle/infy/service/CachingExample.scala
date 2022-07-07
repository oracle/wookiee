/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.infy.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.component.cache._
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.service.messages.Ready

import scala.concurrent.duration._
import scala.util.{Failure, Success}

@SerialVersionUID(1L)
case class ExampleCacheObject(stringValue:String="", intValue:Int=0, boolValue:Boolean=true) extends Cacheable[ExampleCacheObject] {
  override def namespace = "test-cache"
}

/**
 * This example shows how to use caching using the Memcache component
 */
class CachingExample extends Service {

  val Namespace = "test-cache"
  var cacheManager:Option[ActorRef] = None

  implicit val timeout: Timeout = Timeout(2.seconds)

  override def preStart(): Unit = {
    super.preStart()
    getComponent("wookiee-cache-memcache") onComplete {
      case Success(actor) =>
        cacheManager = Some(actor)
        val config = CacheConfig(
          namespace = Namespace,
          props = Some(Map("serverList" -> "util01.cuthbertm.os.optlab.oracle.corp:11211"))
        )
        cacheManager.get ! CreateCache(config)
      case Failure(_) => None
    }
  }

  /**
   * This is the receive expression for your service. Apply any logic you wish
   * to handle specific messages
   */
  override def serviceReceive: Receive = ({
    case Ready(_) =>
      log.info("I received a Ready message from the outside world")
      executeManualCaching()
      executeObjectCaching()
  }: Receive) orElse super.serviceReceive

  private def executeManualCaching(): Unit = {
    cacheManager match {
      case Some(s) =>
        s ! Add(Namespace, "foo", "bar".getBytes("UTF-8"), Some(15))
        // sleep 1 second so that we know that the object is in the cache
        Thread.sleep(1000L)
        (s ? Get(Namespace, "foo")).mapTo[Option[Array[Byte]]] onComplete {
          case Success(value) =>
            log.info(s"Retrieved value ${new String(value.get)} for key `foo`")
          case Failure(f) => log.error(f, "Failed to retrieve value for `foo`")
        }

      case None => log.error("Cache Manager was never initialized")
    }
  }

  private def executeObjectCaching(): Unit = {
    cacheManager match {
      case Some(s) =>
        val cKey = Some(new CacheKey(0, "testKey"))
        val obj1 = ExampleCacheObject("stringvalue", 5)
        obj1.writeInCache(s, cKey)
        Thread.sleep(1000L)
        ExampleCacheObject().readFromCache(s, cKey) onComplete {
          case Success(s) => log.info(s.get.toString)
          case Failure(f) => log.error(f, s"Failed to retrieve object for ${cKey.toString}")
        }
      case None => log.error("Cache Manager was never initialized")
    }
    ()
  }
}

