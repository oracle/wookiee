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

package com.webtrends.harness.policy

import akka.actor.{ActorRef}

import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.BaseCommandHelper

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


/**
 * A trait that you can add to any actor that will enable the actor to talk to the PolicyManager easily
 * and execute commands at will
 *
 * @author Peter Crossley 8/19/2015
 */
trait PolicyHelper extends BaseCommandHelper {
  import scala.concurrent.ExecutionContext.Implicits.global

  var policyManagerInitialized = false
  var policyManager:Option[ActorRef] = None

  def initPolicyHelper = {
    addPolicies
  }

  def initPolicyManager : Future[Boolean] = {
    val p = Promise[Boolean]
    policyManagerInitialized match {
      case true => p success policyManagerInitialized
      case false =>
        actorSystem.actorSelection(HarnessConstants.PolicyFullName).resolveOne()(2 seconds) onComplete {
          case Success(s) =>
            policyManagerInitialized = true
            policyManager = Some(s)
            p success policyManagerInitialized
          case Failure(f) => p failure PolicyException("Policy Manager", f)
        }
    }
    p.future
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  def addPolicies = {}
}
