package com.oracle.infy.wookiee.utils

import com.oracle.infy.wookiee.model.{CaseInsensitiveKey, CaseInsensitiveMap}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class CaseInsensitiveMapSpec extends AnyWordSpec with Matchers {

  "CaseInsensitiveMap" when {

    "constructed" should {
      "handle empty default value" in {
        val map = CaseInsensitiveMap.empty[Int]
        intercept[NoSuchElementException] {
          map.default("no-key")
        }
        intercept[NoSuchElementException] {
          map.empty("no-key")
        }
      }

      "handle non-empty map with default value" in {
        val map = CaseInsensitiveMap(Map("One" -> 1), Some(0))
        map.default("anything") mustBe 0
      }
    }

    "accessing elements" should {
      val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2, "Three" -> 3)

      "return default value for non-existent keys" in {
        val mapWithDefault = CaseInsensitiveMap(Map("One" -> 1), Some(0))
        mapWithDefault.default("Four") mustBe 0
      }

      "handle applyOrElse correctly" in {
        val result = map.applyOrElse("Four", (_: String) => 4)
        result mustBe 4
      }

      "handle getOrElse correctly" in {
        val result = map.getOrElse("Four", 4)
        result mustBe 4

        val result2 = map.getOrElse("oNe", 4)
        result2 mustBe 1
      }
    }

    "case insensitive keys" should {
      "work on equality" in {
        val key1 = CaseInsensitiveKey("sOmEkEy")
        key1.equals(CaseInsensitiveKey("somekey")) mustBe true
        key1.equals(CaseInsensitiveKey("someotherkey")) mustBe false
        key1.equals("somekey") mustBe true
        key1.equals(Map()) mustBe false
      }

      "provide a hashcode" in {
        val key1 = CaseInsensitiveKey("sOmEkEy")
        key1.hashCode mustBe CaseInsensitiveKey("somekey").hashCode
        key1.hashCode must not be CaseInsensitiveKey("someotherkey").hashCode
      }

      "handle unapplys" in {
        val key1 = CaseInsensitiveKey("sOmEkEy")
        CaseInsensitiveKey.unapply(key1) mustEqual Some("sOmEkEy")
      }
    }

    "modifying elements" should {
      val map = CaseInsensitiveMap("One" -> 1)

      "handle updatedWith correctly" in {
        val newMap = map.updatedWith("One")(_.map(_ + 10))
        newMap("One") mustBe 11
      }

      "handle updatedWith when it remaps to None" in {
        val newMap = map.updatedWith("One")(_ => None)
        newMap.contains("One") mustBe false
      }

      "remove all specified elements with removedAll" in {
        val newMap = map ++ Map("Three" -> 3, "Four" -> 4)
        val afterRemoval = newMap.removedAll(List("One", "Four"))
        afterRemoval.contains("One") mustBe false
        afterRemoval.contains("Four") mustBe false
      }
    }

    "using iterator" should {
      "iterate over all elements maintaining their original case" in {
        val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2)
        val elements = map.iterator.toSet
        elements must contain theSameElementsAs Set("One" -> 1, "Two" -> 2)
      }
    }

    "modifying elements" should {
      val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2)

      "add an element with +" in {
        val newMap = map + ("Three" -> 3)
        newMap.get("Three") mustBe Some(3)
        newMap.size mustBe 3
      }

      "remove an element with removed" in {
        val newMap = map.removed("One")
        newMap.contains("One") mustBe false
        newMap.size mustBe 1
      }

      "update an existing element with updated" in {
        val newMap = map.updated("Two", 22)
        newMap.get("Two") mustBe Some(22)
      }

      "add a new element with updated" in {
        val newMap = map.updated("Three", 3)
        newMap.contains("Three") mustBe true
      }
    }

    "iterating and size" should {
      val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2)

      "execute a function on each element with foreach" in {
        var sum = 0
        map.foreach { case (_, value) => sum += value }
        sum mustBe 3
      }
    }

    "using map" should {
      val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2)

      "handle map with new default value" in {
        val newMap = map.map({ case (k, v) => (k, v.toString) }, Some("0"))
        newMap("no-key") mustBe "0"
      }

      "handle the map with no default value" in {
        val newMap = map.map({ case (k: String, v: Int) => (k, v.toString) }, None)
        intercept[NoSuchElementException] {
          newMap("no-key")
        }
      }
    }

    "checking contains" should {
      val map = CaseInsensitiveMap("One" -> 1, "Two" -> 2)

      "report true for existing key" in {
        map.contains("One") mustBe true
      }

      "report false for non-existing key" in {
        map.contains("Three") mustBe false
      }
    }

    "java map creation" should {
      "create a java map" in {
        val basicMap = Map("string" -> "value")
        val ciMap = CaseInsensitiveMap(basicMap.asJava)
        ciMap("StRiNg") mustEqual "value"

        val ciMap2 = CaseInsensitiveMap(basicMap.asJava, "default")
        ciMap2("not-present") mustEqual "default"
        ciMap2("StRiNg") mustEqual "value"
      }
    }

    "empty map behavior" should {
      val emptyMap = CaseInsensitiveMap.empty[Int]

      "report size as 0 for empty map" in {
        emptyMap.size mustBe 0
      }

      "not find any elements in an empty map" in {
        emptyMap.get("Any") mustBe None
      }
    }
  }
}
