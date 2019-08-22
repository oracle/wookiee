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

package com.webtrends.harness.service.test

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import ch.qos.logback.classic.Level
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.HarnessConstants._
import com.webtrends.harness.app.Harness
import com.webtrends.harness.app.HarnessActor.{GetManagers, ReadyCheck}
import com.webtrends.harness.component.{Component, LoadComponent}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.LoadService

import scala.concurrent.Await
import scala.concurrent.duration._

object TestHarness {
  var harness:Option[TestHarness] = None

  /**
   * Create a new instance of the test harness and start all of it's components
   * @param config the config to use
   */
  def apply(config:Config,
            serviceMap:Option[Map[String, Class[_ <: Service]]]=None,
            componentMap:Option[Map[String, Class[_ <: Component]]]=None,
            logLevel:Level=Level.INFO,
            timeToWait:FiniteDuration=15.seconds) : TestHarness = {
    harness match {
      case Some(h) => h
      case None =>
        harness = Some(new TestHarness(config).start(serviceMap, componentMap, logLevel, timeToWait))
        harness.get
    }
  }

  def system: Option[ActorSystem] = Harness.getActorSystem
  def log: Logger = Harness.getLogger
  def rootActor: Option[ActorRef] = Harness.getRootActor

  def shutdown: Unit = harness match {
    case Some(h) => h.stop
    case None => // ignore
  }
}

class TestHarness(conf:Config) {
  var services: Map[String, ActorRef] = Map[String, ActorRef]()
  var components: Map[String, ActorRef] = Map[String, ActorRef]()
  var serviceManager: Option[ActorRef] = None
  var componentManager: Option[ActorRef] = None
  var commandManager: Option[ActorRef] = None
  var policyManager: Option[ActorRef] = None
  var config: Config = conf.withFallback(defaultConfig)
  config = config.withFallback(config.getConfig("wookiee-system")).resolve()

  implicit val timeout: Timeout = Timeout(4000, TimeUnit.MILLISECONDS)

  def start(serviceMap:Option[Map[String, Class[_ <: Service]]]=None,
            componentMap:Option[Map[String, Class[_ <: Component]]]=None,
            logLevel:Level=Level.ERROR,
            timeToWait:FiniteDuration=15.seconds) : TestHarness = {
    Harness.externalLogger.info("Starting Harness...")
    Harness.externalLogger.info(s"Test Harness Config: ${config.toString}")
    Harness.addShutdownHook()
    Harness.startActorSystem(Some(config))
    // after we have started the TestHarness we need to set the serviceManager, ComponentManager and CommandManager from the Harness
    harnessReadyCheck(timeToWait.fromNow)
    Await.result(TestHarness.rootActor.get ? GetManagers, 5.seconds) match {
      case m =>
        val map = m.asInstanceOf[Map[String, ActorRef]]
        serviceManager = map.get(ServicesName)
        policyManager = map.get(PolicyName)
        commandManager = map.get(CommandName)
        componentManager = map.get(ComponentName)
        TestHarness.log.info("Managers all accounted for")
    }
    setLogLevel(logLevel)
    if (componentMap.isDefined) {
      loadComponents(componentMap.get)
    }
    if (serviceMap.isDefined) {
      loadServices(serviceMap.get)
    }
    this
  }

  def stop: Unit = {
    Harness.shutdownActorSystem(block = false) {
      // wait a second to make sure it shutdown correctly
      Thread.sleep(1000)
    }
  }

  def setLogLevel(level:Level): Unit = {
    TestHarness.log.setLogLevel(level)
  }

  def harnessReadyCheck(timeOut: Deadline) {
    while(!timeOut.isOverdue() && !Await.result(TestHarness.rootActor.get ? ReadyCheck, 10.seconds).asInstanceOf[Boolean]) {
    }

    if (timeOut.isOverdue()) {
      throw new IllegalStateException("HarnessActor did not start up")
    }
  }

  def getService(service: String): Option[ActorRef] = {
    services.get(service)
  }

  def getServiceOrDie(service: String): ActorRef = {
    services.getOrElse(service,
      throw new IllegalStateException(s"No such service registered: $service, available services: ${services.keySet.mkString(",")}"))
  }

  def getComponent(component: String): Option[ActorRef] = {
    components.get(component)
  }

  def getComponentOrDie(component: String): ActorRef = {
    components.getOrElse(component,
      throw new IllegalStateException(s"No such component registered: $component, available components: ${components.keySet.mkString(",")}"))
  }

  def loadComponents(componentMap: Map[String, Class[_ <: Component]]): Unit = {
    componentMap foreach { p =>
      componentReady(5.seconds.fromNow, p._1, p._2.getCanonicalName)
    }
  }
  
  def loadServices(serviceMap: Map[String, Class[_ <: Service]]): Unit = {
    serviceMap foreach { p =>
      serviceReady(5.seconds.fromNow, p._1, p._2)
    }
  }

  private def componentReady(timeOut: Deadline, componentName: String, componentClass: String) {
    if (timeOut.isOverdue()) {
      throw new IllegalStateException(s"Component $componentName did not start up")
    }
    Await.result(componentManager.get ? LoadComponent(componentName, componentClass), 5.seconds) match {
      case Some(m) =>
        val component = m.asInstanceOf[ActorRef]
        TestHarness.log.info(s"Loaded component $componentName, ${component.path.toString}")
        components += (componentName -> component)
      case None =>
        throw new Exception("Component not returned")
    }
  }

  private def serviceReady(timeOut: Deadline, serviceName: String, serviceClass: Class[_ <: Service]) {
    if (timeOut.isOverdue()) {
      throw new IllegalStateException(s"Service $serviceName did not start up")
    }
    Await.result(serviceManager.get ? LoadService(serviceName, serviceClass), 3.seconds) match {
      case Some(m) =>
        val service = m.asInstanceOf[ActorRef]
        TestHarness.log.info(s"Loaded service $serviceName, ${service.path.toString}")
        services += (serviceName -> service)
      case None =>
        throw new Exception("Service not returned")
    }
  }

  def defaultConfig : Config = {
    ConfigFactory.parseString(
      """
        wookiee-system {
          prepare-to-shutdown-timeout = 1
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
          default-nr-routees = 1
        }
      """)
  }
}
