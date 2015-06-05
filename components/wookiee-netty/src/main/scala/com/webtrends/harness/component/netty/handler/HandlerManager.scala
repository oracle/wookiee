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

package com.webtrends.harness.component.netty.handler

import io.netty.channel.ChannelHandler
import org.slf4j.LoggerFactory

import scala.collection.mutable.{HashMap, SynchronizedMap}

/**
 * Created by wallinm on 12/10/14.
 */
object HandlerManager {
  val log = LoggerFactory.getLogger(this.getClass)

  private val handlers = new HashMap[String, () => Map[String, ChannelHandler]] with SynchronizedMap[String, () => Map[String, ChannelHandler]]

  def addGetHandlers(name: String, handlerFunc: () => Map[String, ChannelHandler]): Unit = {
    handlers += name -> handlerFunc
  }

  def addGetHandler(name:String, channelHandler: ChannelHandler): Unit = {
    val func = () => Map(name -> channelHandler)
    handlers += name -> func
  }

  def removeHandler(name: String, handler: ChannelHandler) = {
    log.debug(s"handler unregistered with handler manager [${handler.toString}]")
    handlers.remove(name)
  }

  def getHandler(name: String): Option[() => Map[String, ChannelHandler]] = handlers.get(name)

  def getHandlers: Map[String, ChannelHandler] = {
    handlers.values.foldLeft(Map[String, ChannelHandler]()) { (z,f) =>
      z ++ f()
    }
  }
}
