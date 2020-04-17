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

package com.webtrends.harness.utils

import akka.actor.{ActorRef, ActorContext, Props}
import akka.routing.{FromConfig, RoundRobinPool}
import org.slf4j.LoggerFactory

/**
 * @author Michael Cuthbert on 1/28/15.
 */
object AkkaUtil {

  val externalLogger = LoggerFactory.getLogger(this.getClass)

  /**
   * This function helps a user create a actor based on Config from the system configuration using
   * akka.actor.deployment {
   *  /[ACTOR_PATH]/[ACTOR_NAME] {
   *    router = round-robin
   *    nr-of-instances = 5
   *  }
   * }
   * This is based on the Akka FromConfig function and functionality around actor routees. This function
   * is useful because it checks to see if configuration is available for the routees, and if not if defaults
   * to using Round-Robin with 3 routees.
   *
   * @param props The props for the actor class, using defined like Props[ActorClass] or Props(clazz, construtor_arg1, ...)
   * @param actorName The name that you wish the actor to be
   * @param defNumRoutees DEFAULT=3 If config not found, then will default to the number of routees you put in
   * @param context this is the implicit context that would be usually defined by the actor that you are calling this function from
   * @return
   */
  def initActorFromConfig(props:Props, actorName:String, defNumRoutees:Int=3)(implicit context:ActorContext) : ActorRef = {
    val config = context.system.settings.config
    val deployPath = s"akka.actor.deployment.${context.self.path.toStringWithoutAddress}/$actorName"
    if (!config.hasPath(deployPath)) {
      // message primarily for debugging so that you can see immediately if your actor found the config
      externalLogger.debug(s"Could not find deployment config for path [$deployPath], deploying default round robin with $defNumRoutees routees")
      context.actorOf(RoundRobinPool(defNumRoutees).props(props), actorName)
    } else {
      context.actorOf(FromConfig.props(props), actorName)
    }
  }
}
