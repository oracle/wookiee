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

package com.webtrends.harness.component.socko.route

import akka.actor.ActorContext
import org.slf4j.LoggerFactory

import scala.collection.mutable

object SockoRouteManager {
  val externalLogger = LoggerFactory.getLogger(this.getClass)
  private val handlerMap = new mutable.LinkedHashMap[String, List[(String, SockoRouteHandler)]]

  def addHandler(name:String, handler:SockoRouteHandler, method: String)(implicit context:ActorContext): Unit = {
    addHandlers(name, handler, Seq(method))(context)
  }

  def addHandlers(name:String, handler:SockoRouteHandler, methods: Seq[String])(implicit context:ActorContext) {
    methods foreach { method =>
      handlerMap.getOrElseUpdate(method.toUpperCase, List()).forall { it => it._1 != name } match {
        case true => handlerMap.put(method.toUpperCase, handlerMap(method.toUpperCase) :+ (name, handler))
        case false => externalLogger.debug(s"Socko Route Handler $name already added, discarding")
      }
    }
  }

  def getHandler(name:String): Option[SockoRouteHandler] = {
    handlerMap.values.foreach(it => it.foreach(handler => if (handler._1 == name) return Some(handler._2)))
    None
  }

  def getHandlers(method: String): Option[List[(String, SockoRouteHandler)]] = {
    handlerMap.get(method.toUpperCase)
  }
}