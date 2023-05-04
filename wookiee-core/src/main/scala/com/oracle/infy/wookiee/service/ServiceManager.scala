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

import akka.actor.SupervisorStrategy.{Escalate, Restart, Stop}
import akka.actor._
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.HarnessActor.{ConfigChange, SystemReady}
import com.oracle.infy.wookiee.app.PrepareForShutdown
import com.oracle.infy.wookiee.component.{ComponentInfo, ComponentReady}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent, WookieeMonitor}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.ServiceManager.{RestartService, ServicesReady}
import com.oracle.infy.wookiee.service.messages.{LoadService, Ready}
import com.oracle.infy.wookiee.service.meta.{ServiceMetaData, ServiceMetaDataV2}
import com.typesafe.config.{Config, ConfigFactory}

import java.io.{File, FilenameFilter}
import java.nio.file._
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.Exception._

class ServiceManager extends PrepareForShutdown with ServiceLoader {
  val readyComponents = new ConcurrentHashMap[String, ComponentInfo]()

  import context.dispatcher

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1.minute) {
      case _: ActorInitializationException => Stop
      case _: DeathPactException           => Stop
      case _: ActorKilledException         => Restart
      case _: Exception                    => Restart
      case _: Throwable                    => Escalate
    }

  override def preStart(): Unit = {

    load(context)
    // Tell the harness that the services are loaded. The parent will then
    // tell us when it is ready so that the services can be notified
    context.parent ! ServicesReady
    log.info("Service Manager started")
  }

  override def postStop(): Unit = {
    log.info("Service Manager stopped")
  }

  override def getDependents: Iterable[WookieeMonitor] =
    Option(serviceRef.get).collect {
      case meta: ServiceMetaDataV2 => meta.service
    }.toList

  override def prepareForShutdown(): Unit = {
    oldAndNewLogic(
      it => it, // Do nothing as this is propagated on the receive method
      service => service.propagatePrepareForShutdown()
    )
    ()
  }

  override def receive: Receive = super.receive orElse {
    case SystemReady =>
      log.debug("Notifying Service that we are completely ready..")
      oldAndNewLogic(
        actor => actor ! Ready(),
        service => service.propagateSystemReady()
      )
      log.info("Wookiee Started, Let's Go")

    case LoadService(name, clazz) =>
      log.info(s"We have received a message to load service [$name]")
      sender() ! context
        .child(name)
        .orElse(context.child(clazz.getSimpleName))
        .orElse(loadService(name, clazz, componentInfos = readyComponents.values().asScala.toList))

    case RestartService() =>
      log.info(s"We have received a message to restart the service")
      tryAndLogError(
        oldAndNewLogic(
          akkaRef => akkaRef ! Restart, { service =>
            service.propagatePrepareForShutdown()
            service.propagateStart()
            readyComponents.values().asScala.foreach(info => service.propagateOnComponentReady(info))
            service.propagateSystemReady()
          }
        )
      )
      ()

    case ConfigChange() =>
      log.debug("Sending config change message to service...")
      context.children foreach { p =>
        p ! ConfigChange()
      }

    case ready: ComponentReady =>
      log.debug(s"Service Manager got Component Ready for [${ready.info.name}]")
      readyComponents.put(ready.info.name, ready.info)
      tryAndLogError(
        oldAndNewLogic(
          akkaRef => akkaRef ! ready,
          service => service.propagateOnComponentReady(ready.info)
        )
      )
      ()
  }

  private def oldAndNewLogic[T](akkaLogic: ActorRef => T, serviceLogic: ServiceV2 => T): Option[T] =
    Option(serviceRef.get()).map {
      case meta: ServiceMetaData =>
        akkaLogic(meta.actorRef)
      case meta: ServiceMetaDataV2 =>
        val service = meta.service
        serviceLogic(service)
    }

  override def getHealth: Future[HealthComponent] = {
    log.debug("Service health requested")
    Future {
      if (Option(serviceRef.get).isEmpty) {
        HealthComponent(
          ServiceManager.ServiceManagerName,
          ComponentState.CRITICAL,
          "There are no services currently installed"
        )
      } else {
        HealthComponent(
          ServiceManager.ServiceManagerName,
          ComponentState.NORMAL,
          s"Managing Wookiee's service layer"
        )
      }
    }
  }
}

object ServiceManager extends LoggingAdapter {

  val ServiceManagerName = "service-manager"

  @SerialVersionUID(1L) case class ServicesReady()

  @SerialVersionUID(2L) case class RestartService()

  def props: Props = Props[ServiceManager]()

  /**
    * Load the configuration files for the deployed services
    * @param sysConfig System level config for wookiee
    */
  def loadConfigs(sysConfig: Config): Seq[Config] = {
    serviceDir(sysConfig) match {
      case Some(s) =>
        val dirs = s.listFiles.filter(_.isDirectory)

        val configs = dirs flatMap { dir =>
          val path = dir.getPath.concat("/conf")
          log.info("Checking the directory {} for any *.conf files to load", path)
          for {
            file <- getConfigFiles(path)
            conf = allCatch either ConfigFactory.parseFile(file) match {
              case Left(fail)   => log.error(s"Could not load the config file ${file.getAbsolutePath}", fail); None
              case Right(value) => Some(value)
            }
            if conf.isDefined
          } yield conf.get
        }
        configs.toList
      case None => Seq()
    }
  }

  /**
    * Get the services directory
    * @param config The systems main config
    * @return The service root path, this is option, so if none then not found
    */
  def serviceDir(config: Config): Option[File] = {
    val file = FileSystems.getDefault.getPath(config.getString(HarnessConstants.KeyServicePath)).toFile
    if (file.exists()) {
      Some(file)
    } else {
      None
    }
  }

  private def getConfigFiles(path: String): Seq[File] = {
    val root = new File(path)
    if (root.exists) {
      root
        .listFiles(new FilenameFilter {
          def accept(dir: File, name: String): Boolean = name.endsWith(".conf")
        })
        .toList
    } else Seq.empty
  }

}
