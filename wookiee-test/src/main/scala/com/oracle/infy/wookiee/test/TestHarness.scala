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

package com.oracle.infy.wookiee.test

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import ch.qos.logback.classic.{Level, LoggerContext}
import com.oracle.infy.wookiee.HarnessConstants._
import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.app.Harness
import com.oracle.infy.wookiee.app.HarnessActor.{GetManagers, ReadyCheck}
import com.oracle.infy.wookiee.component._
import com.oracle.infy.wookiee.health.ComponentState
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.service.messages.LoadService
import com.oracle.infy.wookiee.service.meta.WookieeServiceMeta
import com.oracle.infy.wookiee.test.TestHarness.defaultConfig
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.sun.management.UnixOperatingSystemMXBean
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory
import java.net.ServerSocket
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Random, Try}

object TestHarness extends Mediator[TestHarness] {

  /**
    * Create a new instance of the test harness and start all of it's components.
    * @param config the config to use
    * @param timeToWait after starting, this function will wait this amount of time for Wookiee to "come up"
    * @param serviceMap map of arbitrary names to Service classes that will be loaded up with Test Wookiee
    * @param componentMap map of arbitrary names to Component classes that will be loaded up with Test Wookiee
    */
  def apply(
      config: Config,
      serviceMap: Option[Map[String, Class[_ <: WookieeService]]] = None,
      componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] = None,
      logLevel: Level = Level.INFO,
      timeToWait: FiniteDuration = 15.seconds
  ): TestHarness = {
    val harness = new TestHarness(config, serviceMap, componentMap, logLevel, timeToWait)
    harness
  }

  // Returns an unused port on the current system, useful for avoiding port conflicts
  def getFreePort: Int = {
    val socket = new ServerSocket(0)
    try {
      socket.setReuseAddress(true)
      socket.getLocalPort
    } finally if (Option(socket).nonEmpty) socket.close()
  }

  // Can use this (on linux systems only) to log how many file descriptors are currently held
  // by the jvm, useful for tracking down file/connection leaks
  def logFileHandleCount(prefix: String): Unit = {
    val os = ManagementFactory.getOperatingSystemMXBean
    os match {
      case bean: UnixOperatingSystemMXBean =>
        log.info(prefix + " open files = " + bean.getOpenFileDescriptorCount)
      case _ =>
    }
  }

  def rootActor()(implicit system: ActorSystem): Option[ActorRef] = Harness.getRootActor()

  // Use this to shutdown TestHarness
  def shutdown()(implicit system: ActorSystem): Unit =
    maybeGetMediator(system.settings.config) match {
      case Some(h) =>
        h.stop()
        unregisterMediator(system.settings.config)
      case None => // ignore
    }

  // Can use this to set the logging level to something other than INFO
  def setLogLevel(level: Level): Unit = {
    Try {
      val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
      loggerContext.getLoggerList.forEach(logger => logger.setLevel(level))
    }
    ()
  }

  def defaultConfig: Config = {
    ConfigFactory.parseString(s"""
        wookiee-system {
          prepare-to-shutdown-timeout = 1
          instance-id = "test-${Random.nextInt(1000000)}"
        }
        services {
          path = ""
          distinct-classloader = false
        }
        components {
          path = ""
        }
        test-mode = true
        internal-http {
          enabled = false
        }
        # CIDR Rules
        cidr-rules {
          # This is a list of IP ranges to allow through. Can be empty.
          allow = ["127.0.0.1/30", "10.0.0.0/8"]
          # This is a list of IP ranges to specifically deny access. Can be empty.
          deny = []
        }
        commands {
          # generally this should be enabled
          enabled = true
          default-nr-routees = 5
        }
      """)
  }
}

class TestHarness(
    conf: Config,
    serviceMap: Option[Map[String, Class[_ <: WookieeService]]] = None,
    componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] = None,
    logLevel: Level = Level.ERROR,
    timeToWait: FiniteDuration = 30.seconds
) extends LoggingAdapter {

  var services: Map[String, WookieeServiceMeta] = Map[String, WookieeServiceMeta]()
  var serviceManager: Option[ActorRef] = None
  var componentManager: Option[ActorRef] = None
  var commandManager: Option[ActorRef] = None
  implicit var config: Config = conf.withFallback(defaultConfig)

  private val instanceId: String = Try(TestHarness.getInstanceId(config)).getOrElse("wookiee") match {
    case "wookiee" =>
      val inst = s"test-${Random.nextInt(1000000)}"
      config = ConfigFactory
        .parseString(s"""
             |wookiee-system.instance-id = "$inst"
             |instance-id = "$inst"
             |""".stripMargin)
        .withFallback(config)
      inst
    case other => other
  }
  config = config.withFallback(config.getConfig("wookiee-system")).resolve()

  TestHarness.registerMediator(TestHarness.getInstanceId(config), this)

  implicit val timeout: Timeout = Timeout(timeToWait)

  Harness.externalLogger.info("Starting Wookiee...")
  Harness.externalLogger.info(s"Test Harness Config: ${config.toString}")

  implicit val system: ActorSystem = Harness.startActorSystem(Some(config)).actorSystem

  Harness.addShutdownHook()
  // after we have started the TestHarness we need to set the serviceManager, ComponentManager and CommandManager from the Harness
  harnessReadyCheck(timeToWait.fromNow)
  Await.result((TestHarness.rootActor().get ? GetManagers).mapTo[Map[String, ActorRef]], timeToWait) match {
    case map: Map[String, ActorRef] =>
      serviceManager = map.get(ServicesName)
      commandManager = map.get(CommandName)
      componentManager = map.get(ComponentName)
      TestHarness.log.info("Managers all accounted for")
  }

  TestHarness.setLogLevel(logLevel)
  if (componentMap.isDefined) {
    loadComponents(componentMap.get)
  }
  if (serviceMap.isDefined) {
    loadServices(serviceMap.get)
  }

  def getInstanceId: String = instanceId

  def stop()(implicit system: ActorSystem): Unit =
    Harness.shutdownActorSystem(block = false) {
      // wait a second to make sure it shutdown correctly
      Thread.sleep(1000)
    }

  def harnessReadyCheck(timeOut: Deadline)(implicit system: ActorSystem): Unit = {
    while (!timeOut.isOverdue() && !Await
             .result[Boolean](
               TestHarness
                 .rootActor()
                 .map(act => (act ? ReadyCheck).mapTo[Boolean])
                 .getOrElse(Future.successful(false)),
               timeToWait
             )) {}

    if (timeOut.isOverdue()) {
      throw new IllegalStateException("HarnessActor did not start up")
    }
  }

  def getService(service: String): Option[WookieeServiceMeta] =
    services.get(service)

  def getServiceOrDie(service: String): WookieeServiceMeta =
    services.getOrElse(
      service,
      throw new IllegalStateException(
        s"No such service registered: $service, available services: ${services.keySet.mkString(",")}"
      )
    )

  // @deprecated("Use getComponentAkka instead", "2.4.0")
  def getComponent(component: String): Option[ActorRef] =
    getComponentAkka(component)

  // Returns the ComponentV2 if it exists
  def getComponentV2(componentName: String): Option[ComponentV2] =
    ComponentManager
      .getComponentByName(componentName)
      .collect({
        case c: ComponentInfoV2 => c.component
      })

  // For legacy Components, will eventually be removed
  def getComponentAkka(componentName: String): Option[ActorRef] =
    ComponentManager
      .getComponentByName(componentName)
      .collectFirst({
        case ComponentInfoAkka(_, _, actorRef) => actorRef
      })

  def awaitComponent(name: String, waitMs: Long = 15000L): ComponentV2 =
    ThreadUtil.awaitResult(getComponentV2(name), waitMs)

  // Will wait for a component to be up and its health check to return NORMAL
  def isComponentUp(
      name: String,
      waitMs: Long = 15000L
  )(implicit ec: ExecutionContext): Boolean = {
    def checkComponent(): Future[Boolean] =
      getComponentV2(name).get.checkHealth.map { health =>
        health.state == ComponentState.NORMAL
      }

    Try {
      ThreadUtil.awaitFuture(checkComponent, waitMs)
      true
    }.getOrElse {
      log.warn(s"Component [$name] did not return a NORMAL health check in time")
      false
    }
  }

  def loadComponents(componentMap: Map[String, Class[_ <: WookieeComponent]]): Unit = {
    componentMap foreach { p =>
      componentReady(p._1, p._2.getCanonicalName)
    }
  }

  def loadServices(serviceMap: Map[String, Class[_ <: WookieeService]]): Unit = {
    serviceMap foreach { p =>
      serviceReady(p._1, p._2)
    }
  }

  private def componentReady(componentName: String, componentClass: String): Unit = {
    Await.result(componentManager.get ? LoadComponent(componentName, componentClass), timeToWait) match {
      case Some(_) =>
        TestHarness.log.info(s"Loaded component $componentName")
      case None =>
        throw new Exception(s"Component $componentName not returned")
    }
  }

  private def serviceReady(serviceName: String, serviceClass: Class[_ <: WookieeService]): Unit = {
    Await.result(serviceManager.get ? LoadService(serviceName, serviceClass), timeToWait) match {
      case Some(m) =>
        val service = m.asInstanceOf[WookieeServiceMeta]
        TestHarness.log.info(s"Loaded service $serviceName")
        services += (serviceName -> service)
      case None =>
        throw new Exception(s"Service $serviceName not returned")
    }
  }
}
