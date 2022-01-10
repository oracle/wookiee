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

import akka.actor.ActorSystem
import com.oracle.infy.wookiee.component.ComponentManager
import com.oracle.infy.wookiee.logging.Logger
import com.oracle.infy.wookiee.service.ServiceManager
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigRenderOptions}

import java.io.InputStream
import scala.io.Source

object HarnessActorSystem {

  lazy val loader: HarnessClassLoader = HarnessClassLoader(Thread.currentThread.getContextClassLoader)
  private val externalLogger = Logger.getLogger(this.getClass)

  def apply(config: Config): ActorSystem = {
    ActorSystem.create("server", config, loader)
  }

  // JARs are reloaded onto the classpath in this method if replace = true, in ComponentManager.loadComponentJars
  def renewConfigsAndClasses(config: Option[Config], replace: Boolean = false): Config = {
    def printConf(conf: Config): String =
      conf.root().render(ConfigRenderOptions.concise())

    var sysConfig = {
      if (config.isDefined) {
        config.get
      } else {
        val baseConfig = ConfigFactory.load(loader, "conf/application.conf")
        ConfigFactory.load(loader).withFallback(baseConfig).getConfig("wookiee-system")
      }
    }

    ComponentManager.loadComponentJars(sysConfig, loader, replace = replace)
    for (child <- loader.getChildLoaders) {
      def readRefConf(): Option[Config] = {
        val re = child.getResources("reference.conf")
        while (re.hasMoreElements) {
          val next = re.nextElement()
          if (next.getPath.contains(child.entityName)) {
            val confStr = Source.fromInputStream(next.getContent.asInstanceOf[InputStream]).mkString
            val childConf = ConfigFactory.parseString(confStr)
            externalLogger.info(
              s"New config for extension '${child.entityName}', jar: '${child.urls.head.getPath}': " +
                s"\n${printConf(childConf)}"
            )
            return Some(childConf)
          }
        }
        None
      }

      readRefConf() match {
        case Some(conf) =>
          sysConfig = conf.withFallback(sysConfig)
        case None =>
          externalLogger.warn(s"Didn't find 'reference.conf' in jar file '${child.urls.head.getPath}'")
      }
    }
    ConfigFactory.load

    externalLogger.debug("Loading the service configs")
    val configs = ServiceManager.loadConfigs(sysConfig)
    if (configs.nonEmpty)
      externalLogger.info(
        s"${configs.size} service config(s) have been loaded: \n${configs.map(printConf).mkString(", ")}"
      )

    externalLogger.debug("Loading the component configs")
    val compConfigs = ComponentManager.loadComponentInfo(sysConfig)
    if (compConfigs.nonEmpty)
      externalLogger.info(
        s"${compConfigs.size} component config(s) have been loaded: \n${compConfigs.map(printConf).mkString(", ")}\nIf 0 could be due to config loaded from component JARs."
      )

    val allConfigs = configs ++ compConfigs

    // Build the hierarchy
    val conf =
      if (allConfigs.isEmpty) sysConfig
      else allConfigs.reduce(_.withFallback(_)).withFallback(sysConfig)
    val finalConf = conf.resolve()
    externalLogger.debug(s"Used configuration: \n${printConf(finalConf)}")
    finalConf
  }
}
