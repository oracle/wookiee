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

import akka.pattern._
import akka.actor.{ActorRef, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.utils.ConfigUtil

import scala.concurrent.{Await, Promise, Future}
import scala.util.{Try, Success, Failure}

trait ActorHealth {
  this: Actor =>

  private val _log = Logger(this, context.system)

  import context.dispatcher

  implicit val checkTimeout:Timeout =
    ConfigUtil.getDefaultTimeout(context.system.settings.config, HarnessConstants.KeyDefaultTimeout, Timeout(10 seconds))

  def health:Receive = {
    case CheckHealth =>
      pipe(Try(checkHealth)
        .recover({
        case e: Exception =>
          HealthComponent(getClass.getSimpleName, ComponentState.CRITICAL,
            "Exception when trying to check the health: %s".format(e.getMessage))
      }).get.asInstanceOf[Future[HealthComponent]]
      ) to sender()
  }

  /**
   * This is the health of the current object, by default will be NORMAL
   * In general this should be overridden to define the health of the current object
   * For objects that simply manage other objects you shouldn't need to do anything
   * else, as the health of the children components would be handled by their own
   * CheckHealth function
   *
   * @return
   */
  protected def getHealth: Future[HealthComponent] = {
    Future {
      HealthComponent(self.path.toString, ComponentState.NORMAL, "Healthy")
    }
  }

  /**
   * The actor has been asked to respond with some health information. It needs
   * to implement this function and provide a list of components used in this service
   * and their current state. By default the health check will simply run through all the
   * children for the actor and get their health. Should be overridden for any custom
   * behavior
   * @return An instance of a health component
   */
  def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()

    getHealth.onComplete {
      case Success(s) =>
        val healthFutures = context.children map { ref =>
          (ref ? CheckHealth).mapTo[HealthComponent] recover {
            case ex: Exception => HealthComponent(ref.path.name, ComponentState.CRITICAL, s"Failure to get health of child component. ${ex.getMessage}")
          }
        }

        Future.sequence(healthFutures) onComplete {
          case Failure(f) =>
            _log.debug(f, "Failed to retrieve health of children objects")
            p success HealthComponent(s.name, ComponentState.CRITICAL, s"Failure to get health of child components. ${f.getMessage}")
          case Success(healths) =>
            healths foreach { it => s.addComponent(it) }
            p success s
        }
      case Failure(f) =>
        _log.debug(f, "Failed to get health from component")
        p success HealthComponent(self.path.toString, ComponentState.CRITICAL, f.getMessage)
    }

    p.future
  }
}