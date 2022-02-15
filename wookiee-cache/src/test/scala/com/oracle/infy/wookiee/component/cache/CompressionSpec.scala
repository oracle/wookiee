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

import com.oracle.infy.wookiee.component.cache.BaseSpecCache.ns
import com.oracle.infy.wookiee.component.cache.memory.MemoryManager
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.zip.Deflater
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

case class CompressedData(d: String = "") extends Cacheable[CompressedData] with Compression[CompressedData] {
  override def level: Int = Deflater.BEST_COMPRESSION
  override def namespace: String = ns
}

case class StandardData(d: String = "") extends Cacheable[CompressedData] {
  override def namespace: String = ns
}

class CompressionSpec extends BaseSpecCache with AnyWordSpecLike with Matchers {
  "A cacheable object with Compression" should {
    import system.dispatcher

    "be cacheable" in {
      val obj = CompressedData("Some data")
      val key = new CacheKey(1, "basic", false)
      obj.writeInCache(cacheRef, Some(key))

      val found = Await.result(CompressedData().readFromCache(cacheRef, Some(key)), 10.seconds)
      found mustBe Some(CompressedData("Some data"))
    }

    "be compressed when cached" in {
      val cacheActor = cacheRef.underlyingActor

      val bigData = new Random(System.currentTimeMillis()).nextString(2048)
      val uKey = new CacheKey(1, "uncompressed", false)
      val uObj = StandardData(bigData)
      uObj.writeInCache(cacheRef, Some(uKey))
      val uSize = cacheActor.caches(ns)(uKey.toString()).buffer.length

      val cKey = new CacheKey(1, "compressed", false)
      val cObj = CompressedData(bigData)
      cObj.writeInCache(cacheRef, Some(cKey))
      val cSize = cacheActor.caches(ns)(cKey.toString()).buffer.length

      cSize must be < uSize
      cObj.compressionRatio.get must be > 1.0d
      cObj.compressedSize.get must be < uSize.toLong
    }
  }
}
