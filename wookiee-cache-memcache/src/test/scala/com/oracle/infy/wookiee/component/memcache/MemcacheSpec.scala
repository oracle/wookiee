package com.oracle.infy.wookiee.component.memcache

import com.oracle.infy.wookiee.component.cache.CacheConfig
import com.twitter.finagle.memcached.MockClient
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Await, Duration}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.TimeUnit

class MemcacheSpec extends AnyWordSpecLike with Matchers {

  "Memcache" should {
    "recover data" in {
      val client = new MockClient()
      try {
        val memcache = Memcache(client, CacheConfig("test-cache", "test-namespace", "test-key", None))
        val bytes = "testValue".getBytes
        memcache.set("testKey", new ByteArray(bytes, 0, bytes.length))
        val got = Await.result(memcache.get("testKey"), Duration(3, TimeUnit.SECONDS)).get
        val toStr = got match {
          case Buf.Utf8(str) => str
        }
        toStr mustEqual new String(bytes)
      } finally {
        val _ = client.close()
      }
    }

    "check health" in {
      val client = new MockClient()
      try {
        val memcache = Memcache(client, CacheConfig("test-cache", "test-namespace", "test-key", None))
        val status = Await.result(memcache.checkHealth(), Duration(3, TimeUnit.SECONDS))
        status.connect mustEqual true
      } finally {
        val _ = client.close()
      }
    }

    "increment and decrement" in {
      val client = new MockClient()
      try {
        val memcache = Memcache(client, CacheConfig("test-cache", "test-namespace", "test-key", None))
        val bytes = "0".getBytes
        memcache.set("test-count", new ByteArray(bytes, 0, bytes.length))
        val init = Await.result(memcache.increment("test-count", 3), Duration(3, TimeUnit.SECONDS)).get
        init mustEqual 3
        val end = Await.result(memcache.decrement("test-count", 2), Duration(3, TimeUnit.SECONDS)).get
        end mustEqual 1
      } finally {
        val _ = client.close()
      }
    }

    "handle deletes" in {
      val client = new MockClient()
      try {
        val memcache = Memcache(client, CacheConfig("test-cache", "test-namespace", "test-key", None))
        val bytes = "testValue".getBytes
        memcache.set("testKey", new ByteArray(bytes, 0, bytes.length))
        memcache.delete("testKey")
        val got = Await.result(memcache.get("testKey"), Duration(3, TimeUnit.SECONDS))
        got mustBe None
      } finally {
        val _ = client.close()
      }
    }
  }
}
