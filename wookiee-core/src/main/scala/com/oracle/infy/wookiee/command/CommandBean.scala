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

package com.oracle.infy.wookiee.command

import java.lang.reflect.{Constructor, Parameter}

import com.google.common.primitives.Primitives

import scala.reflect.ClassTag

/**
  * This commandBean should be extended for specific implementation,
  * however can be used just as is. Allows a standard base class to
  * work with though.
  *
  * @author Pete Crossley
  */
sealed trait Bean extends Product

object Bean {
  def apply[T: ClassTag](items: Array[Any]): T = ArraySpawner[T](items)
  def apply[T: ClassTag](m: Map[String, Any]): T = MapSpawner[T](m)

  def infer[T: ClassTag](any: Bean): T = any match {
    case a: ArrayBean => apply(a.array)
    case m: MapBean   => apply(m.map)
  }
}

case class MapBean(map: Map[String, Any]) extends Bean {
  def getValue[T](key: String): Option[T] = map.get(key).map(_.asInstanceOf[T])
}
case class ArrayBean(array: Array[Any]) extends Bean

trait ClassSpawner {

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

object ArraySpawner extends ClassSpawner {

  def apply[T: ClassTag](input: Array[Any]): T = {
    val params: Array[Parameter] = ctor.getParameters
    // validate that types are compatible
    for (i: Int <- params.indices) {
      if (Primitives.wrap(input(i).getClass) != Primitives.wrap(params(i).getType)) {
        throw new IllegalArgumentException(
          s"Field: ${params(i).getName} found with Type: ${input(i).getClass}. should be of Type: ${params(i).getType}"
        )
      }
    }
    ctor
      .newInstance(input.map(_.asInstanceOf[Object]).toArray: _*)
      .asInstanceOf[T]
  }
}

object MapSpawner extends ClassSpawner {

  def apply[T: ClassTag](input: Map[String, Any]): T = {
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
