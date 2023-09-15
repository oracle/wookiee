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
package com.oracle.infy.wookiee.service

import akka.actor._
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.{HActor, HarnessClassLoader}
import com.oracle.infy.wookiee.component.{ComponentInfo, ComponentReady}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.meta.{ServiceMetaData, ServiceMetaDataV2, WookieeServiceMeta}
import com.oracle.infy.wookiee.utils.{ClassUtil, ConfigUtil}

import java.util.concurrent.atomic.AtomicReference
import scala.util.{Failure, Success, Try}

trait ServiceLoader { this: HActor with LoggingAdapter =>
  val serviceRef: AtomicReference[WookieeServiceMeta] = new AtomicReference[WookieeServiceMeta]()

  /**
    * Load the services
    * @param context The service manager's context
    */
  def load(context: ActorContext): Unit = {
    Try({
      val internalService = ConfigUtil.getDefaultValue(HarnessConstants.KeyInternalService, config.getString, "")
      if (internalService.nonEmpty) {
        val loader = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
        val serviceName = internalService.split("\\.").reverse(0)
        loadService(serviceName, loader.loadClass(internalService))
      } else {
        log.warn("WSL100: No 'service.internal' set in config, not loading any service")
      }
    }).recover({
      case e: Throwable => log.error("WSL101: Error loading the service(s)", e)
    })
    ()
  }

  def loadService[T](
      name: String,
      clazz: Class[T],
      componentInfos: List[ComponentInfo] = List()
  ): Option[WookieeServiceMeta] = {
    val serviceInfo: Option[WookieeServiceMeta] = clazz match {
      case c if classOf[Service].isAssignableFrom(c) =>
        Try {
          val serviceActor = context.actorOf(Props(c.asInstanceOf[Class[_ <: Actor]]), name)

          // Watch this actor
          context watch serviceActor
          Some(
            ServiceMetaData(
              name,
              serviceActor
            )
          )

        }.recover {
          case _: InvalidActorNameException =>
            log.info(s"WLS200: Service $name already started")
            Option(serviceRef.get).collect {
              case s: ServiceMetaData => s
            }
          case e: Throwable =>
            log.error(s"WSL201: Error loading the service [$name]", e)
            // Remove the actor so we can avoid a badly loaded actor
            context.child(name) match {
              case Some(actor) => context.unwatch(actor); context.stop(actor)
              case None        => // Do nothing
            }
            None
        } match {
          case Failure(ex) =>
            log.error(s"WSL202: Error loading the service [$name]", ex)
            None
          case Success(None) =>
            None
          case Success(Some(meta)) =>
            log.debug(s"WSL203: Service Loader sending [${componentInfos.size}] Component Ready infos")
            componentInfos.foreach { info =>
              meta.actorRef ! ComponentReady(info) // Send readied Component infos to new Service
            }
            Some(meta)
        }

      // Load the service as a V2 service without akka
      case c if classOf[ServiceV2].isAssignableFrom(c) =>
        try {
          def initServiceV2(name: String, clazz: Class[_ <: ServiceV2]): ServiceMetaDataV2 = {
            log.info(s"WSL204: Loading V2 Service [$name]")
            val serviceStart = ClassUtil.instantiateClass(clazz, config)

            serviceStart.propagateStart()
            componentInfos.foreach { info =>
              serviceStart.propagateOnComponentReady(info) // Send readied Component infos to new Service
            }
            ServiceMetaDataV2(name, serviceStart)
          }

          Some(initServiceV2(name, c.asSubclass(classOf[ServiceV2])))
        } catch {
          case ex: Throwable =>
            log.error(s"WSL205: Failed to load Service class [$clazz] for [$name]", ex)
            None
        }

      case _ =>
        log.error(s"WSL206: Could not load service $name, not assignable from ${clazz.getName}")
        None
    }

    // Register the service if we have successfully loaded it
    serviceInfo.foreach(serviceRef.set)
    serviceInfo
  }
}
