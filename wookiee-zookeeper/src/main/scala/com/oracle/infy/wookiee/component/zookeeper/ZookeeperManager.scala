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
package com.oracle.infy.wookiee.component.zookeeper

import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.typesafe.config.Config

class ZookeeperManager(name: String, config: Config) extends ComponentV2(name, config) with Zookeeper {
  override def getDependents: Iterable[WookieeMonitor] = ZookeeperService.maybeGetMediator(config).toList

  override def start(): Unit = {
    if (!isClusterEnabled) {
      log.info("Starting Zookeeper Component...")
      startZookeeper()
    } else {
      log.info("Zookeeper Component Started, but letting wookiee-cluster start its actors...")
    }
  }

  override def prepareForShutdown(): Unit = {
    stopZookeeper()
    super.prepareForShutdown()
  }
}

object ZookeeperManager {
  def ComponentName = "wookiee-zookeeper"
}
