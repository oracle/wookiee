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
package com.webtrends.harness.app

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.component.ComponentManager
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.ServiceManager

object HarnessActorSystem {

  lazy val loader = HarnessClassLoader(Thread.currentThread.getContextClassLoader)
  private val externalLogger = Logger.getLogger(this.getClass)

  def apply(config:Option[Config]=None): ActorSystem = {
    ActorSystem.create("server", getConfig(config), loader)
  }

  def getConfig(config:Option[Config]): Config = {
    val sysConfig = {
      if (config.isDefined) {
        config.get
      } else {
        val baseConfig = ConfigFactory.load(loader, "conf/application.conf")
        ConfigFactory.load(loader).withFallback(baseConfig).getConfig("wookiee-system")
      }
    }

    ComponentManager.loadComponentJars(sysConfig, loader)
    ConfigFactory.load

    externalLogger.debug("Loading the service configs")
    val configs = ServiceManager.loadConfigs(sysConfig)
    if (configs.nonEmpty) externalLogger.info(s"${configs.size} service config(s) have been loaded: ${configs.mkString(", ")}")

    externalLogger.debug("Loading the component configs")
    val compConfigs = ComponentManager.loadComponentInfo(sysConfig)
    if (compConfigs.nonEmpty) externalLogger.info(s"${compConfigs.size} component config(s) have been loaded: ${compConfigs.mkString(", ")}\nIf 0 could be due to config loaded from component JARs.")

    val allConfigs = configs ++ compConfigs

    // Build the hierarchy
    val conf = if (allConfigs.isEmpty) sysConfig
      else allConfigs.reduce(_.withFallback(_)).withFallback(sysConfig)
    conf.resolve()
  }
}