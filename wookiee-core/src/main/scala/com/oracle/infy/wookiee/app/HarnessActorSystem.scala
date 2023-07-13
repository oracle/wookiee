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
import com.oracle.infy.wookiee.app.WookieeSupervisor.loader
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import java.io.InputStream
import scala.io.Source

object HarnessActorSystem extends LoggingAdapter {

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

    WookieeComponent.loadComponentJars(sysConfig, loader, replace = replace)
    for (child <- loader.getChildLoaders) {
      def readRefConf(): Option[Config] = {
        val re = child.getResources("reference.conf")
        while (re.hasMoreElements) {
          val next = re.nextElement()
          if (next.getPath.contains(child.entityName)) {
            val confStr = Source.fromInputStream(next.getContent.asInstanceOf[InputStream]).mkString
            val childConf = ConfigFactory.parseString(confStr)
            log.info(
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
          sysConfig = sysConfig.withFallback(conf)
        case None =>
          log.warn(s"Didn't find 'reference.conf' in jar file '${child.urls.head.getPath}'")
      }
    }
    ConfigFactory.load

    log.debug("Loading the service configs")
    val configs = WookieeSupervisor.loadConfigs(sysConfig)
    if (configs.nonEmpty)
      log.info(
        s"${configs.size} service config(s) have been loaded: \n${configs.map(printConf).mkString(", ")}"
      )

    log.debug("Loading the component configs")
    val compConfigs = WookieeComponent.loadComponentInfo(sysConfig)
    if (compConfigs.nonEmpty)
      log.info(
        s"${compConfigs.size} component config(s) have been loaded: \n${compConfigs.map(printConf).mkString(", ")}\nIf 0 could be due to config loaded from component JARs."
      )

    val allConfigs = configs ++ compConfigs

    // Build the hierarchy
    val conf =
      if (allConfigs.isEmpty) sysConfig
      else allConfigs.reduce(_.withFallback(_)).withFallback(sysConfig)
    val finalConf = conf.resolve()
    log.debug(s"Used configuration: \n${printConf(finalConf)}")
    finalConf
  }
}
