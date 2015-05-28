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

package com.webtrends.harness.component.spray.serialization

import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/**
 * @author Michael Cuthbert on 1/13/15.
 */
class EnumerationSerializer(enums: Enumeration*) extends Serializer[Enumeration#Value] {
  val EnumerationClass = classOf[Enumeration#Value]
  val formats = Serialization.formats(NoTypeHints)
  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Enumeration#Value] = {
    case (TypeInfo(EnumerationClass, _), json) => json match {
      case JString(value) => enums.find(_.values.exists(_.toString == value)).get.withName(value)
      case value          => throw new MappingException("Can't convert " + value + " to " + EnumerationClass)
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case i: Enumeration#Value => i.toString
  }
}
