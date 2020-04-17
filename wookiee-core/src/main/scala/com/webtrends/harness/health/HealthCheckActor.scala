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
package com.webtrends.harness.health

import akka.actor.{Props, Status}
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.HActor
import com.webtrends.harness.utils.ConfigUtil

import scala.util.{Failure, Success}

object HealthCheckActor {
  def props: Props = Props[HealthCheckActor]

  // These objects will be temporary enough, favoring time complexity concerns over space concerns
  protected[health] def collectHealthStates(health: ApplicationHealth): collection.mutable.Map[Seq[String], ComponentState.ComponentState] = {
    val checks = collection.mutable.Map.empty[Seq[String], ComponentState.ComponentState]

    def drillDown(parentPath: Seq[String], check: HealthComponent): Unit = {
      checks.+=((parentPath :+ check.name, check.state))
      check.components.foreach(c => drillDown(parentPath :+ check.name, c))
    }

    checks.+=((Seq(health.applicationName), health.state))
    health.components.foreach(c => drillDown(Seq(health.applicationName), c))
    checks
  }

  protected[health] def healthChecksDiffer(previous: ApplicationHealth, current: ApplicationHealth): Boolean = {
    val previousStates = collectHealthStates(previous)
    var foundDiff = false
    def drillDown(parentPath: Seq[String], check: HealthComponent): Unit =
      if (!foundDiff) {
        val previous = previousStates.get(parentPath :+ check.name)
        if (!previous.contains(check.state))
          foundDiff = true
        else
          check.components.foreach(c => drillDown(parentPath :+ check.name, c))
      }

    current.components.foreach(c => drillDown(Seq(current.applicationName), c))
    previous.state != current.state ||
      foundDiff
  }
}

class HealthCheckActor extends HActor with HealthCheckProvider {

  private var previousCheck: Option[ApplicationHealth] = None

  override def preStart() {
    log.info("Health Manager started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    log.info("Health Manager stopped: {}", context.self.path)
  }

  override def receive = health orElse {
    case HealthRequest(typ) =>
      val caller = sender
      log.debug("Fetching the system health")
      import context.dispatcher
      runChecks onComplete {
        case Success(s) =>
          comparePreviousCheck(s)
          val res = typ match {
            case HealthResponseType.NAGIOS => "%s|%s".format(s.state.toString.toUpperCase, s.details)
            case HealthResponseType.LB => if (s.state == ComponentState.CRITICAL) "DOWN" else "UP"
            case _ => s
          }
          caller ! res
        case Failure(f) => caller ! Status.Failure(f)
      }
  }

  private def comparePreviousCheck(health: ApplicationHealth): Unit =
    if (ConfigUtil.getDefaultValue(HarnessConstants.LogHealthCheckDiffs, config.getBoolean, false)) {
      previousCheck match {
        case Some(c) =>
          if (HealthCheckActor.healthChecksDiffer(c, health))
            log.info(s"Health check status changed. Old: ${c.toJson()} New: ${health.toJson()}")
        case None => // Not much use checking against nothing
      }
      previousCheck = Some(health)
    }
}