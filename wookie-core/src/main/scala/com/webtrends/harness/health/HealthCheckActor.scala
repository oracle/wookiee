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
package com.webtrends.harness.health

import akka.actor.{Status, Props}
import com.webtrends.harness.app.HActor

import scala.util.{Failure, Success}

object HealthCheckActor {
  def props: Props = Props[HealthCheckActor]
}

class HealthCheckActor extends HActor with HealthCheckProvider {

  override def preStart() {
    log.info("Health manager started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    log.info("Health manager stopped: {}", context.self.path)
  }

  override def receive = health orElse {
    case HealthRequest(typ) =>
      val caller = sender
      log.debug("Fetching the system health")
      import context.dispatcher
      runChecks onComplete {
        case Success(s) =>
          val res = typ match {
            case HealthResponseType.NAGIOS => "%s|%s".format(s.state.toString.toUpperCase, s.details)
            case HealthResponseType.LB => if (s.state == ComponentState.CRITICAL) "DOWN" else "UP"
            case _ => s
          }
          caller ! res
        case Failure(f) => caller ! Status.Failure(f)
      }
  }
}