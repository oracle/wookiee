/*
 * Copyright 2015 Oracle (http://www.Oracle.com)
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
package com.oracle.infy.wookiee.component.metrics

import akka.actor.{ActorRef, PoisonPill}
import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.app.HarnessActor
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.metrics.monitoring.MonitoringSettings
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.utils.ConfigUtil
import com.typesafe.config.Config

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class MetricsManager(name: String, config: Config) extends ComponentV2(name, config) {
  implicit val monitorSettings: MonitoringSettings = MonitoringSettings(ConfigUtil.prepareSubConfig(config, name))
  private val metricsActor: AtomicReference[ActorRef] = new AtomicReference[ActorRef]()
  private implicit val timeout: Timeout = Timeout(15.seconds)

  override def onRequest[T](msg: T): Any = msg match {
    case sr: StatusRequest =>
      Option(metricsActor.get()) match {
        case Some(actor) =>
          actor ? sr
        case None =>
          Future.failed(new IllegalStateException("Metrics actor not initialized"))
      }
  }

  /**
    * Starts the component
    */
  override def start(): Unit = {
    // Hookup the main communication actor
    metricsActor.set(
      HarnessActor.getMediator(config).actorOf(MetricsActor.props(monitorSettings), MetricsManager.MetricsName)
    )
    super.start()
  }

  override def prepareForShutdown(): Unit = {
    super.prepareForShutdown()
    Try(metricsActor.get() ! PoisonPill)
    ()
  }

  override protected def getHealth: Future[HealthComponent] = {
    Future.successful {
      HealthComponent(
        MetricsManager.ComponentName,
        ComponentState.NORMAL,
        "Wookiee Metrics Up",
        None,
        List(MetricsActor.health)
      )
    }
  }
}

object MetricsManager {
  val ComponentName = "wookiee-metrics"
  val MetricsName = "metrics"
}
