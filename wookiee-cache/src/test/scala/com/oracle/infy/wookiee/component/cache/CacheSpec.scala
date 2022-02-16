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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.Await

case class SimpleData(a: Int = 0, b: String = "", c: Double = 0.0) extends Cacheable[SimpleData] {
  override def namespace: String = ns
}

case class SerialData(a: Int = 0, b: String = "", c: Double = 0.0) extends Cacheable[SimpleData] with Serializable {
  override def namespace: String = ns

  override protected def getBytes: Array[Byte] = serialToBytes(this)

  override protected def extract(obj: Array[Byte])(implicit m: Manifest[SimpleData]): Option[SimpleData] = {
    bytesToSerial(obj)
  }
}

class CacheSpec extends BaseSpecCache with AnyWordSpecLike with Matchers {
  "A cacheable object" should {
    import system.dispatcher

    "be cacheable" in {
      val obj = SimpleData(1, "two", 3.0)
      val key = new CacheKey(1, "two", false)
      obj.writeInCache(cacheRef, Some(key))

      val found = Await.result(SimpleData().readFromCache(cacheRef, Some(key)), 10.seconds)
      found mustBe Some(SimpleData(1, "two", 3.0))
    }

    "be cacheable with five" in {
      val obj = SerialData(4, "five", 6.0)
      val key = new CacheKey(4, "five", false)
      obj.writeInCache(cacheRef, Some(key))

      val found = Await.result(SerialData().readFromCache(cacheRef, Some(key)), 10.seconds)
      found mustBe Some(SerialData(4, "five", 6.0))
    }
  }
}
