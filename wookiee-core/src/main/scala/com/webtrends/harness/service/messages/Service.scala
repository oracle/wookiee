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
package com.webtrends.harness.service.messages

import akka.actor.ActorPath
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.meta.ServiceMetaData

sealed trait ServiceMessage

case class CheckHealth() extends ServiceMessage

case class Ping() extends ServiceMessage

case class Pong() extends ServiceMessage

case class Ready(meta: ServiceMetaData) extends ServiceMessage

case class GetMetaDetails() extends ServiceMessage

case class GetMetaData(service: Option[ActorPath] = None) extends ServiceMessage

case class LoadService(name: String, clazz: Class[_ <: Service]) extends ServiceMessage
