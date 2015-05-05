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
package com.webtrends.harness.service

import akka.actor.SupervisorStrategy.{Escalate, Restart, Stop}
import akka.actor._
import com.typesafe.config.{ConfigFactory, Config}
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.{PrepareForShutdown, HActor}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.LoggingAdapter
import com.webtrends.harness.service.ServiceManager.RestartService
import com.webtrends.harness.service.ServiceManager.{GetMetaDataByName, ServicesReady}
import com.webtrends.harness.service.messages.{LoadService, GetMetaData, Ready}
import com.webtrends.harness.service.meta.ServiceMetaData
import com.webtrends.harness.app.HarnessActor.{PrepareForShutdown, ConfigChange, SystemReady}
import java.io.{FilenameFilter, File}
import java.nio.file._
import scala.Predef._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.control.Exception._

/**
 * Request to return service meta data
 */
class ServiceManager extends PrepareForShutdown with ServiceLoader {

  import context.dispatcher

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute) {
      case _: ActorInitializationException => Stop
      case _: DeathPactException => Stop
      case _: ActorKilledException => Restart
      case _: Exception => Restart
      case _: Throwable => Escalate
    }

  override def preStart {

    load(context)
    // Tell the harness that the services are loaded. The parent will then
    // tell us when it is ready so that the services can be notified
    context.parent ! ServicesReady
    log.info("Service manager started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    // Kill any custom class loaders
    services.values foreach {
      p =>
        if (p._2.isDefined) p._2.get.close
    }
    services.clear
    log.info("Service manager stopped: {}", context.self.path)
  }

  override def receive = super.receive orElse {
    case SystemReady =>
      log.info("Notifying Services that we are completely ready.")
      context.children foreach {
        p => p ! Ready(getServiceMeta(Some(p.path)).head)
      }

    case GetMetaData(path) =>
      log.info("We have received a message to get service meta data")
      val meta = getServiceMeta(path)
      path match {
        case None => sender() ! meta // Asked for all services
        case _ => sender() ! meta.head // Asked for only a specific service
      }

    case LoadService(name, clazz) =>
      log.info(s"We have received a message to load service $name")
      sender ! loadService(name, clazz)

    case GetMetaDataByName(service) =>
      log.info("We have received a message to get service meta data")
      services.filter(_._1.name.equalsIgnoreCase(service)).keys.headOption match {
        case Some(meta) => sender() ! meta
        case None => sender() ! Status.Failure(new NoSuchElementException(s"Could not locate the meta information for service $service"))
      }

    case RestartService(service) =>
      log.info(s"We have received a message to restart the service ${service}")
      val meta = services.filter(_._1.name.equalsIgnoreCase(service)).keys.headOption match {
        case Some(meta) => services.get(meta).get._1 ! Kill
        case None =>
      }

    case c: ConfigChange =>
      log.info("Sending config change message to all services...")
      context.children foreach {
        p => p ! c
      }

    case Terminated(service) =>
      log.info("Service {} terminated", service.path.name)
      // Find and nuke the classloader
      services.filter(_._1.akkaPath.equals(service.path.toString)) foreach {
        p =>
          if (p._2._2.isDefined) {
            p._2._2.get.close
          }
          services.remove(p._1)
      }
  }

  private def getServiceMeta(servicePath: Option[ActorPath]): Seq[ServiceMetaData] = {
    try {
      log.debug("Service meta requested")
      servicePath match {
        case None =>
          services.keys.toSeq
        case _ =>
          Seq(services.filter(p => ActorPath.fromString(p._1.akkaPath).equals(servicePath.get)).keys.headOption.get)
      }
    } catch {
      case e: Throwable =>
        log.error("Error fetching service meta information", e)
        Nil
    }
  }


  /**
   * This is the health of the current object, by default will be NORMAL
   * In general this should be overridden to define the health of the current object
   * For objects that simply manage other objects you shouldn't need to do anything
   * else, as the health of the children components would be handled by their own
   * CheckHealth function
   *
   * @return
   */
  override protected def getHealth: Future[HealthComponent] = {
    log.debug("Service health requested")
    Future {
      if (services.size == 0) {
          HealthComponent(ServiceManager.ServiceManagerName, ComponentState.CRITICAL, "There are no services currently installed")
      } else if (context.children.size != services.size) {
          HealthComponent(ServiceManager.ServiceManagerName, ComponentState.CRITICAL, s"There are ${services.size} installed, but only ${context.children.size} that were successfully loaded")
      } else {
        HealthComponent(ServiceManager.ServiceManagerName, ComponentState.NORMAL, s"Currently managing ${services.size} service(s)")
      }
    }
  }
}

object ServiceManager extends LoggingAdapter {

  val ServiceManagerName = "service-manager"

  @SerialVersionUID(1L) case class ServicesReady()

  @SerialVersionUID(1L) case class GetMetaDataByName(name: String)

  @SerialVersionUID(1L) case class RestartService(name: String)

  def props: Props = Props[ServiceManager]

  /**
   * Load the configuration files for the deployed services
   * @param sysConfig
   * @return
   */
  def loadConfigs(sysConfig: Config): Seq[Config] = {
    serviceDir(sysConfig) match {
      case Some(s) =>
        val dirs = s.listFiles.filter(_.isDirectory)

        val configs = dirs flatMap {
          dir =>
            val path = dir.getPath.concat("/conf")
            log.info("Checking the directory {} for any *.conf files to load", path)
            for {
              file <- getConfigFiles(path)
              conf = allCatch either (ConfigFactory.parseFile(file)) match {
                case Left(fail) => log.error(s"Could not load the config file ${file.getAbsolutePath}", fail); None
                case Right(value) => Some(value)
              }
              if (conf.isDefined)
            } yield conf.get
        }
        configs
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
    root.exists match {
      case true =>
        root.listFiles(new FilenameFilter {
          def accept(dir: File, name: String): Boolean = name.endsWith(".conf")
        })
      case false =>
        Seq.empty
    }
  }

}
