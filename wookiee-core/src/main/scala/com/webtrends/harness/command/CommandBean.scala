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

import scala.collection.mutable

/**
 * This commandBean should be extended for specific implementation,
 * however can be used just as is. Allows a standard base class to
 * work with though.
 *
 * @author Michael Cuthbert on 12/1/14.
 */
@SerialVersionUID(100L)
class CommandBean extends mutable.HashMap[String, AnyRef] {

  def appendMap(params:Map[String, AnyRef]) = params foreach { this += _ }

  def addValue(key:String, value:AnyRef) = this += key -> value

  def getValue[T](key:String) : Option[T] = {
    get(key) match {
      case Some(k) => Some(k.asInstanceOf[T])
      case None => None
    }
  }
}

object CommandBean {
  val KeyEntity = "Request-Entity"
  val KeyPath = "Selected-Path"

  def apply(params:Map[String, AnyRef]) = {
    val bean = new CommandBean()
    params foreach {
      bean += _
    }
    bean
  }
}
