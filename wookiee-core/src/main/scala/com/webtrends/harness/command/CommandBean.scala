/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
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

package com.webtrends.harness.command
import com.webtrends.harness.macros.mapper.Mappable

import scala.collection.mutable

/**
 * This commandBean should be extended for specific implementation,
 * however can be used just as is. Allows a standard base class to
 * work with though.
 *
 * @author Pete Crossley
 */

case class CommandBean[T:Product](override val map: Map[String, Any] = Map.empty) extends BaseCommandBean[T]

trait BaseCommandBean[T] extends mutable.HashMap[String, Any] {
  protected[this] implicit def map: Map[String, Any] = Map.empty

  //add request map to self
  if (map.nonEmpty) {
    this.addValues(map)
  }

  val data:T = materialize[T](map)

  private def materialize[T: Mappable](map: Map[String, Any]) = implicitly[Mappable[T]].fromMap(map)

  def addValues(map: Map[String, Any]) = this ++= map

  def addValue(key:String, value:Any) = this += key -> value

  def getValue[T](key:String) : Option[T] = {
    this.get(key) match {
      case Some(k) => Some(k.asInstanceOf[T])
      case None => None
    }
  }
}