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

import java.lang.reflect.{Constructor, Parameter}

import com.google.common.primitives.Primitives

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * This commandBean should be extended for specific implementation,
 * however can be used just as is. Allows a standard base class to
 * work with though.
 *
 * @author Pete Crossley
 */

trait CommandBean[T] extends BaseCommandBean[T]

class DefaultCommandBean[T:ClassTag] extends CommandBean[T]

class MapCommandBean(map:Map[String, Any]) extends CommandBean[MapBeanData] {
  override implicit def materialize: Option[MapBeanData] = Some(MapBeanData(map))

  //add to the data bag
  this ++= map

}

case class MapBeanData(map: Map[String, Any]) extends CommandBeanData

abstract class BaseCommandBean[T:ClassTag] extends mutable.HashMap[String, Any] {

  implicit def materialize: Option[T] = { None }

  private val _data: Option[T] = materialize

  def hasData = _data.isDefined

  def data: T = _data.get

  def addValues(map: Map[String, Any]) = this ++= map

  def addValue(key: String, value: Any) = this += key -> value

  def getValue[V](key: String): Option[V] = {
    this.get(key) match {
      case Some(k) => Some(k.asInstanceOf[V])
      case None => None
    }
  }
}

trait CommandBeanData extends Product

trait ClassSpawner[I] {
  def apply[T: ClassTag](input: I): T

  // gets constructor for T
  protected def ctor[T: ClassTag]: Constructor[_] = {
    val clazz: Class[T] = implicitly[reflect.ClassTag[T]]
      .runtimeClass
      .asInstanceOf[Class[T]]
    val ctors: Array[Constructor[_]] = clazz
      .getConstructors
      .filter(_.getParameterTypes.nonEmpty)
    if (ctors.isEmpty) {
      throw new RuntimeException("Constructor not available")
    }
    ctors.head
  }
}

object ArraySpawner extends ClassSpawner[Array[Any]] {
  override def apply[T: ClassTag](input: Array[Any]): T = {
    val params: Array[Parameter] = ctor.getParameters
    // validate that types are compatible
    for (i: Int <- params.indices) {
      if (Primitives.wrap(input(i).getClass) != Primitives.wrap(params(i).getType)) {
        throw new IllegalArgumentException(
          s"Field: ${params(i).getName} found with Type: ${input(i).getClass}. should be of Type: ${params(i).getType}"
        )
      }
    }
    ctor.
      newInstance(input.map(_.asInstanceOf[Object]).toArray: _*)
      .asInstanceOf[T]
  }
}

object MapSpawner extends ClassSpawner[Map[String, Any]] {
  override def apply[T: ClassTag](input: Map[String, Any]): T = {
    val params: Array[Parameter] = ctor.getParameters
    val marshalled: Array[Any] = new Array[Any](params.length)
    for (i: Int <- params.indices) {
      input.get(params(i).getName) match {
        case Some(value: Any) =>
          marshalled(i) = value
        case None =>
          throw new IllegalArgumentException(s"Missing field: ${params(i).getName}")
      }
    }
    ArraySpawner(marshalled)
  }
}

object Create {
  def apply[T: ClassTag](items: Array[Any]): T = ArraySpawner[T](items)
  def apply[T: ClassTag](m: Map[String, Any]): T = MapSpawner[T](m)
}