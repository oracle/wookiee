/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.utils

import java.text.NumberFormat
import java.util.Locale
import scala.util.Sorting

trait JsonSerializable {
  def toJson: String
}

trait JsonLocalization {
  JsonSerializable =>

  def toJson: String =
    toJson(None)

  def toJson(l: Option[Locale]): String
}

/**
  * An Exception thrown when parsing or building JSON.
  */
class JsonException(reason: String) extends Exception(reason)

/**
  * An explanation of Scala types and their JSON representations.
  *
  * Natively supported scalar types are: Boolean, Int, Long, String.
  * Collections are Sequence[T], Map[String, T] where T includes the scalars defined above, or
  * recursive Sequence or Map. You are in flavor country.
  */
object Json {

  private[Json] def quotedChar(codePoint: Int) = {
    codePoint match {
      case c if c > 0xffff =>
        val chars = Character.toChars(c)
        "\\u%04x\\u%04x".format(chars(0).toInt, chars(1).toInt)
      case c if c > 0x7e => "\\u%04x".format(c)
      case c             => c.toChar
    }
  }

  /**
    * Quote a string according to "JSON rules".
    */
  def quote(s: String): String = {
    val charCount = s.codePointCount(0, s.length)
    "\"" + 0
      .until(charCount)
      .map { idx =>
        s.codePointAt(s.offsetByCodePoints(0, idx)) match {
          case 0x0d => "\\r"
          case 0x0a => "\\n"
          case 0x0c => "\\f"
          case 0x09 => "\\t"
          case 0x22 => "\\\""
          case 0x5c => "\\\\"
          case 0x2f => "\\/" // to avoid sending "</"
          case c    => quotedChar(c)
        }
      }
      .mkString("") + "\""
  }

  /**
    * Returns a JSON representation of the given object, as a JsonQuoted object.
    */
  def build(obj: Any, sort: Boolean = true, locale: Option[Locale] = None): JsonQuoted = {
    val rv = obj match {
      case JsonQuoted(body) => body
      case null             => "null"
      case x: Boolean       => x.toString
      case x: Number        => locale.fold(x.toString)(l => quote(NumberFormat.getInstance(l).format(x)))
      case array: Array[_]  => array.map(build(_, sort, locale).body).mkString("[", ",", "]")
      case list: Seq[_] =>
        list.map(build(_, sort, locale).body).mkString("[", ",", "]")
      case map: collection.mutable.LinkedHashMap[_, _] =>
        map.map { case (k, v) => quote(k.toString) + ":" + build(v, sort, locale).body }.mkString("{", ",", "}")
      case map: scala.collection.Map[Any, Any] @unchecked =>
        val finalMap =
          if (sort) Sorting.stableSort[(Any, Any), String](map.iterator.toList, { case (k, _) => k.toString }).toMap
          else map
        finalMap
          .map {
            case (k: Any, v: Any) =>
              quote(k.toString) + ":" + build(v, sort, locale).body
            case (k: Any, null) =>
              quote(k.toString) + ":" + build(null, sort, locale).body
          }
          .mkString("{", ",", "}")
      case x: JsonLocalization => x.toJson(locale)
      case x: JsonSerializable => x.toJson
      case x =>
        quote(x.toString)
    }
    JsonQuoted(rv)
  }
}

/**
  * Wrapper for the JSON string representation of a data structure. This class exists to
  * allow objects to be converted into JSON, attached to another data structure, and not
  * re-encoded.
  */
case class JsonQuoted(body: String) {
  override def toString: String = body
}
