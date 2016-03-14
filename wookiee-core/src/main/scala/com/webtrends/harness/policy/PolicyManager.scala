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

import akka.pattern.{ask, pipe}
import akka.actor.{ActorRef, Props}
import akka.routing.{RoundRobinPool, FromConfig}
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.{PrepareForShutdown, HActor}
import com.webtrends.harness.app.HarnessActor.SystemReady
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure}

case class GetPolicies()


/**
 * @author Peter Crossley on 08/19/15.
 */
class PolicyManager extends PrepareForShutdown {

  import context.dispatcher

  override def receive = super.receive orElse {
    case GetPolicies => pipe(getPolicies) to sender

    case SystemReady => // ignore
  }

  protected def getPolicies : Future[Map[String, Policy]] = {
    Future { PolicyManager.getPolicies.get }
  }

}

object PolicyManager {
  private val externalLogger = LoggerFactory.getLogger(this.getClass)

  // map that stores the name of the command with the actor it references
  val policyMap = mutable.Map[String, Policy]()

  def props = Props[PolicyManager]

  def addPolicy[T<:Policy](name:String, ref:T) = {
    ref.addCommands
    externalLogger.debug(s"Policy $name inserted into Policy Manager map.")
    policyMap += (name -> ref)
  }

  protected def removePolicy(name:String) : Boolean = {
    policyMap.get(name) match {
      case Some(n) =>
        externalLogger.debug(s"Policy $name removed from Policy Manager map.")
        policyMap -= name
        true
      case None => false
    }
  }

  def getPolicy(name:String) : Option[Policy] = policyMap.get(name)

  def getPolicies : Option[Map[String, Policy]] = Some(policyMap.toMap)
}
