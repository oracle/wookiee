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

import com.twitter.finagle.memcached.Client
import com.twitter.io.Buf
import com.twitter.util.{Future, Promise}
import com.oracle.infy.wookiee.component.cache.CacheConfig
import com.oracle.infy.wookiee.component.metrics.TimerStopwatch
import com.oracle.infy.wookiee.component.metrics.metrictype.Histogram

import scala.util.Try

case class Memcache(client: Client, config: CacheConfig) {

  val insertedDataBytes = Histogram(s"memcache.${config.namespace}.inserted-data-bytes")
  val failedDataBytes = Histogram(s"memcache.${config.namespace}.failed-data-bytes")

  def get(key: String): Future[Option[Buf]] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.get")
    val p = Promise[Option[Buf]]()
    val ck = getCacheKey(key)
    ck onSuccess { cKey =>
      val f = client.get(cKey)
      val _ = f onSuccess { data =>
        if (data.isDefined) timer.success() else timer.failure()
        p.setValue(data)
      } onFailure { ex =>
        timer.failure()
        p.setException(ex)
      }
    }
    p
  }

  def set(key: String, value: Buf): Future[Boolean] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.set")

    // Will need to be updated if we ever use non array backed ChannelBuffers
    val dataSize = value.length

    val p = Promise[Boolean]()
    val ck = getCacheKey(key)
    ck onSuccess { cKey =>
      timer.success()
      insertedDataBytes.update(dataSize)
      client.set(cKey, value)
      p.setValue(true)
    } onFailure { ex =>
      timer.failure()
      failedDataBytes.update(dataSize)
      p.setException(ex)
    }
    p
  }

  def delete(key: String): Future[Boolean] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.delete")
    val p = Promise[Boolean]()
    val ck = getCacheKey(key)
    ck onSuccess { cKey =>
      val result = client.delete(cKey)
      val _ = result onSuccess { data =>
        timer.success()
        p.setValue(data)
      } onFailure { ex =>
        timer.failure()
        p.setException(ex)
      }
    }
    p
  }

  def increment(key: String, delta: Long): Future[Option[Long]] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.increment")
    val p = Promise[Option[Long]]()
    val ck = getCacheKey(key)
    ck onSuccess { cKey =>
      val result = client.incr(cKey, delta)
      val _ = result onSuccess { res =>
        timer.success()
        p.setValue(res.map(Long.unbox))
      } onFailure { ex =>
        timer.failure()
        p.setException(ex)
      }
    }
    p
  }

  def decrement(key: String, delta: Long): Future[Option[Long]] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.decrement")
    val p = Promise[Option[Long]]()
    val ck = getCacheKey(key)
    ck onSuccess { cKey =>
      val result = client.decr(cKey, delta)
      val _ = result onSuccess { res =>
        timer.success()
        p.setValue(res.map(Long.unbox))
      } onFailure { ex =>
        timer.failure()
        p.setException(ex)
      }
    }
    p
  }

  def getCacheKey(key: String): Future[String] = {
    val timer = new TimerStopwatch(s"memcache.${config.namespace}.getCacheKey")
    val p = Promise[String]()
    val currentSet = _getCurrentSet
    currentSet onSuccess { cKey =>
      timer.success()
      p.setValue(_getKey(key, cKey))
    } onFailure { _ =>
      timer.failure()
      p.setValue(_getKey(key, -1))
    }
    p
  }

  private def _getKey(key: String, set: Int): String = {
    val setKeyPrefix = if (set > -1) set.toString else ""
    setKeyPrefix + "." + config.namespace + "." + key
  }

  private def _getCurrentSet: Future[Int] = {
    val p = Promise[Int]()
    if (config.setKey.isEmpty) {
      p.setValue(-1)
    } else {
      val f = client.get(config.setKey)
      f onSuccess {
        case Some(buffer) =>
          p.setValue(
            if (buffer.length > 0) Try(buffer.get(0).toInt).getOrElse(-1) else -1
          )
        case None => p.setValue(-1)
      } onFailure { _ =>
        p.setValue(-1)
      }
    }
    p
  }

  def checkHealth(): Future[CacheStatus] = {
    val p = Promise[CacheStatus]()
    val future = client.stats()
    future onSuccess { reply =>
      p.setValue(CacheStatus(connect = true, "Cache looking good %s".format(reply.toString)))
    } onFailure { fail =>
      p.setValue(CacheStatus(connect = false, fail.getMessage))
    }
    p
  }

  def close: Future[Unit] = {
    client.quit()
  }
}
