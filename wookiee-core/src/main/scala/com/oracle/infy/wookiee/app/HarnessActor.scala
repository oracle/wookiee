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
package com.oracle.infy.wookiee.app

import akka.actor.SupervisorStrategy.{Escalate, Restart, Stop}
import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import HarnessActor.PrepareForShutdown
import com.oracle.infy.wookiee.command.CommandManager
import com.oracle.infy.wookiee.component.{
  ComponentInfo,
  ComponentManager,
  ComponentReady,
  ComponentReloadActor,
  InitializeComponents
}
import com.oracle.infy.wookiee.config.ConfigWatcher
import com.oracle.infy.wookiee.{HarnessConstants, health}
import com.oracle.infy.wookiee.health.{ActorHealth, ComponentState, Health, HealthComponent}
import com.oracle.infy.wookiee.http.InternalHTTP
import com.oracle.infy.wookiee.logging.ActorLoggingAdapter
import com.oracle.infy.wookiee.service.ServiceManager
import com.oracle.infy.wookiee.service.ServiceManager.ServicesReady
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ConfigUtil
import scala.jdk.CollectionConverters._

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

object HarnessActor {
  def props(): Props = Props[HarnessActor]()

  @SerialVersionUID(2L) case class ShutdownSystem()
  @SerialVersionUID(2L) case class RestartSystem()
  @SerialVersionUID(1L) case class ConfigChange()
  @SerialVersionUID(1L) case class SystemReady()
  @SerialVersionUID(1L) case class ComponentInitializationComplete()
  @SerialVersionUID(1L) case class ReadyCheck()
  @SerialVersionUID(1L) case class GetManagers()
  @SerialVersionUID(1L) case object PrepareForShutdown
  @SerialVersionUID(1L) case object ForwardComponentInfo
}

// Below are actor traits that are commonly used for actors in the Harness
trait HActor extends Actor with ActorLoggingAdapter with ActorHealth {
  // Globally accessible config loaded from -Dconfig.file=$file_path
  lazy val config: Config = context.system.settings.config
  // By default routes to health check, should make sure to orElse to here if overriding
  override def receive: Receive = health
}

trait PrepareForShutdown extends HActor {

  override def receive: Receive = health orElse {
    case PrepareForShutdown =>
      log.debug("Preparing for shutdown of self and children")
      context.children foreach (_ ! PrepareForShutdown)
  }
}

class HarnessActor extends Actor with ActorLoggingAdapter with Health with ConfigWatcher with InternalHTTP {

  import ConfigUtil._
  import HarnessActor._
  import context.dispatcher

  private val config = context.system.settings.config
  val readyComponents = new ConcurrentHashMap[String, ComponentInfo]()

  implicit val checkTimeout: Timeout =
    getDefaultTimeout(config, HarnessConstants.KeyDefaultTimeout, Timeout(15.seconds))
  val startupTimeout: Timeout = getDefaultTimeout(config, HarnessConstants.KeyStartupTimeout, Timeout(20.seconds))

  val prepareShutdownTimeout: Timeout =
    getDefaultTimeout(config, HarnessConstants.PrepareToShutdownTimeout, Timeout(5.seconds))

  var running: Boolean = false

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1.minute, loggingEnabled = true) {
      case _: ActorInitializationException => Stop
      case _: DeathPactException           => Stop
      case _: ActorKilledException         => Restart
      case _: Exception                    => Restart
      case _: Throwable                    => Escalate
    }

  // The service manager will be created after we have started and all of the system's actors are able to receive messages
  var serviceActor: Option[ActorRef] = None
  var componentActor: Option[ActorRef] = None
  var componentReloadActor: Option[ActorRef] = None
  var commandManager: Option[ActorRef] = None
  var dispatchManager: Option[ActorRef] = None

  // The actor that watches for changes in the harness configuration file and sends out messages when config changes are detected
  var configWatcherActor: Option[ActorRef] = None

  override def preStart(): Unit = initialize()

  override def receive: Receive = initializing

  def initializing: Receive = {
    case CheckHealth =>
      pipe(getHealth(true)) to sender(); ()
    case ComponentInitializationComplete =>
      initializationComplete(); ()
    case ComponentReady(info) => // Store until we're ready to send off to Service
      readyComponents.put(info.name, info); ()
    case ShutdownSystem =>
      shutdownCoreServices(); ()
    case ReadyCheck =>
      sender() ! running
  }

  def processing: Receive = {
    case CheckHealth =>
      pipe(getHealth(false)) to sender(); ()
    case ForwardComponentInfo =>
      sendComponentInfoToService(readyComponents.values().asScala.toList)
    case ComponentReady(info) =>
      readyComponents.put(info.name, info)
      sendComponentInfoToService(List(info))
    case ServicesReady =>
      // This message is sent from the service manager that tells us the services are loaded.
      // Notify the services, components and commands that we are all ready to go
      List(serviceActor, componentActor, dispatchManager, commandManager)
        .flatten
        .foreach(_ ! SystemReady)
    case ReadyCheck => sender() ! running
    case GetManagers =>
      sender() ! Map[String, Option[ActorRef]](
        HarnessConstants.CommandName -> commandManager,
        HarnessConstants.ServicesName -> serviceActor,
        HarnessConstants.ComponentName -> componentActor
      ).collect { case (key, Some(value)) => key -> value }
    case RestartSystem =>
      Harness.restartActorSystem()(context.system)
    case ConfigChange() =>
      log.debug("Received message to reload services/components due to config change")
      serviceActor.get ! ConfigChange()
      componentActor.get ! ConfigChange()
    case ShutdownSystem =>
      shutdownCoreServices()
  }

  /**
    * Start the core services
    */
  private def initialize(): Unit = {
    startHealth
    startConfigWatcher
    if (!config.hasPath(HarnessConstants.KeyCommandsEnabled) || config.getBoolean(HarnessConstants.KeyCommandsEnabled)) {
      commandManager = Some(context.actorOf(CommandManager.props, HarnessConstants.CommandName))
      log.info("Command Manager started: {}", commandManager.get.path)
    }
    componentActor = Some(context.actorOf(ComponentManager.props, HarnessConstants.ComponentName))
    log.info("Component Manager started: {}", componentActor.get.path)
    componentActor.get ! InitializeComponents
  }

  private def initializationComplete(): Unit = {
    // Wait for the child actors above to be loaded before calling on the services
    Future.traverse(context.children)(child => (child ? Identify("xyz123"))(startupTimeout)) onComplete {
      case Success(_) =>
        context.become(processing)
        // Load any services
        serviceActor = Some(context.actorOf(ServiceManager.props, HarnessConstants.ServicesName))
        log.debug("Harness Manager started: {}", context.self.path)
        val cl = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
        componentReloadActor =
          Some(context.actorOf(Props(classOf[ComponentReloadActor], cl), HarnessConstants.ComponentReloadName))
        // in general the internal http should always start, but in the cases where you want to turn it off
        // you can just disable it in the config using internal-http.enable = false
        // it will also fail silently with a warning if another http component is using the same port as it.
        if (ConfigUtil.getDefaultValue(HarnessConstants.KeyInternalHttpEnabled, config.getBoolean, true)) {
          startInternalHTTP(ConfigUtil.getDefaultValue(HarnessConstants.KeyInternalHttpPort, config.getInt, 8080))
        }
        running = true
        self ! ForwardComponentInfo
      case Failure(t) =>
        log.error("Error loading the main harness actors", t)
    }
  }

  /**
    * Complete the shutdown process. This will be called after clustering has been shutdown.
    */
  private def shutdownCoreServices(): Unit = {
    log.info("Starting the shutdown process")
    if (running) {
      val tmpService = serviceActor
      val tmpComp = componentActor
      val tmpCmd = commandManager
      val tmpDis = dispatchManager
      prepareForShutdown(tmpService, tmpDis, tmpCmd, tmpComp) andThen {
        case _ => Try(gracefulShutdown())
      }
    } else Try(gracefulShutdown())

    ()
  }

  private def sendComponentInfoToService(infos: List[ComponentInfo]): Unit = serviceActor match {
    case Some(actor) =>
      log.debug(s"Sending info for started Components [$infos] to Service")
      infos.foreach { info =>
        actor ! ComponentReady(info)
      }
    case None =>
      log.warn("Somehow the Service Actor isn't started to get Component Info")
  }

  private def prepareForShutdown(actorRefs: Option[ActorRef]*): Future[Unit] = {
    Future {
      log.debug(s"Prepare For Shutdown")
      for {
        optRef <- actorRefs
        ref <- optRef
      } yield {
        ref ! PrepareForShutdown
      }
      //Give the message time to propagate through the system.
      Thread sleep prepareShutdownTimeout.duration.toMillis
    }
  }

  private def gracefulShutdown(): Unit = {
    def gStop(actOpt: Option[ActorRef]): Future[Boolean] = {
      if (actOpt != null && actOpt.isDefined) gracefulStop(actOpt.get, checkTimeout.duration)
      else Future.successful(true)
    }

    log.debug(s"Starting graceful shutdown of Service and Component Managers.")
    // Shutdown the Services
    Try(gStop(serviceActor)).map(_.onComplete { _ =>
      // Shutdown the Components
      Try(gStop(componentActor)).map(_.onComplete { _ =>
        // Shutdown the children
        Try(Future.sequence {
          Try(context.children).getOrElse(List()) map { a =>
            gStop(Option(a))
          }
        } onComplete { _ =>
          Try {
            log.info("Harness subsystems have been shutdown")
            context.stop(self)
          }
          running = false
        })
      })
    })
    ()
  }

  /**
    * Fetch the health of this actor and all of its children.
    * @return A Future that contains a sequence of the children's HealthComponent
    */
  private def getHealth(initializing: Boolean): Future[Seq[HealthComponent]] = {
    log.debug("We have received a message to check our health")
    if (initializing) {
      Future {
        Seq(health.HealthComponent("system", ComponentState.DEGRADED, s"The system is still initializing"))
      }
    } else {
      // Call the sections and get their health
      val future = Future.traverse(context.children) { a: ActorRef =>
        log.info("Getting the actor ref ::" + a.path.name)
        val healthComponent = (a ? CheckHealth)(0.5.second).mapTo[HealthComponent]
        log.info("checking the health component :: " + healthComponent.value)
        healthComponent
      }

      val p = Promise[Seq[HealthComponent]]()
      future.onComplete({
        case Failure(f) =>
          log.info("Into failure of future :: ")
          p failure f
        case Success(answers) =>
          log.info("Into success of future :: ")
          p success answers.toSeq
      })

      p.future
    }
  }

}
