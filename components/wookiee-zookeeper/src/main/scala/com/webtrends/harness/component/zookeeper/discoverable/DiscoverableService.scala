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
package com.webtrends.harness.component.zookeeper.discoverable

import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}

import scala.concurrent.Future

/**
 * @author Michael Cuthbert on 7/9/15.
 */
private[harness] class DiscoverableService()(implicit system: ActorSystem) {
  import DiscoverableService._

  private[zookeeper] val defaultTimeout = Timeout(system.settings.config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  private val log = Logging(system, this.getClass)

  def queryForNames(basePath:String)(implicit timeout:Timeout = defaultTimeout) : Future[util.Collection[String]] = {
    (mediator.get ? QueryForNames(basePath)).mapTo[util.Collection[String]]
  }

  def queryForInstances(basePath:String, name:String, id:Option[String]=None)
                       (implicit timeout:Timeout = defaultTimeout): Future[Iterable[ServiceInstance[Void]]] = {
    (mediator.get ? QueryForInstances(basePath, name, id)).mapTo[Iterable[ServiceInstance[Void]]]
  }

  def makeDiscoverable(basePath:String, id:String, name:String, address:Option[String], port:Int, uriSpec:UriSpec)
                      (implicit timeout:Timeout = defaultTimeout): Future[Boolean] = {
    (mediator.get ? MakeDiscoverable(basePath, id, name, address, port, uriSpec)).mapTo[Boolean]
  }

  def getInstance(basePath:String, name:String)(implicit timeout:Timeout) : Future[ServiceInstance[Void]] = {
    (mediator.get ? GetInstance(basePath, name)).mapTo[ServiceInstance[Void]]
  }

  def getAllInstances(basePath:String, name:String)(implicit timeout:Timeout) : Future[Iterable[ServiceInstance[Void]]] = {
    (mediator.get ? GetAllInstances(basePath, name)).mapTo[Iterable[ServiceInstance[Void]]]
  }
}

object DiscoverableService {
  def apply()(implicit system: ActorSystem): DiscoverableService = new DiscoverableService

  private var mediator: Option[ActorRef] = None

  private[harness] def registerMediator(actor: ActorRef) = {
    mediator = Some(actor)
  }

  private[harness] def unregisterMediator(actor: ActorRef) = {
    mediator = None
  }

  @SerialVersionUID(1L) private[harness] case class QueryForNames(basePath:String)

  @SerialVersionUID(1L) private[harness] case class QueryForInstances(basePath:String, name:String, id:Option[String]=None)

  @SerialVersionUID(1L) private[harness] case class MakeDiscoverable(basePath:String, id:String, name:String, address:Option[String], port:Int, uriSpec:UriSpec)

  @SerialVersionUID(1L) private[harness] case class GetInstance(basePath:String, name:String)

  @SerialVersionUID(1L) private[harness] case class GetAllInstances(basePath:String, name:String)
}
