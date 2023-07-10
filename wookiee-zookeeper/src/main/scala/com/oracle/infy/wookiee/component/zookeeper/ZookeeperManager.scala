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

import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.health.HealthComponent
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class ZookeeperManager(name: String, config: Config) extends ComponentV2(name, config) with Zookeeper {

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

  override protected def getHealth: Future[HealthComponent] = super.getHealth.flatMap { health =>
    implicit val timeout: Timeout = new Timeout(10.seconds)

    // Ask underlying zookeeper actor for health
    ZookeeperService
      .maybeGetMediator(ZookeeperService.getInstanceId(config))
      .map { zkActor =>
        (zkActor ? CheckHealth).mapTo[HealthComponent].map { zkHealth =>
          health.addComponent(zkHealth)
        }
      }
      .getOrElse(Future.successful(()))
      .map(_ => health)
  }
}

object ZookeeperManager {
  def ComponentName = "wookiee-zookeeper"
}
