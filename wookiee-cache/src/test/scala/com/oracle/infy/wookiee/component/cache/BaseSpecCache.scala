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

import java.util.concurrent.TimeUnit._

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import com.oracle.infy.wookiee.component.cache.memory.MemoryManager

class BaseSpecCache extends TestKit(ActorSystem("test")) {
  val cacheRef: TestActorRef[MemoryManager] = TestActorRef(Props(classOf[MemoryManager], "test-memory-cache"))
  implicit val timeout: Timeout = Timeout(2, SECONDS)

  cacheRef ! CreateCache(
    CacheConfig(
      namespace = BaseSpecCache.ns
    )
  )
}

object BaseSpecCache {
  val ns = "test-cache"
}
