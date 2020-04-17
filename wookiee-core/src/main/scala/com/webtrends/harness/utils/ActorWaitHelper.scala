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

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.Await

object ActorWaitHelper {
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props, system: ActorSystem, actorName: Option[String] = None)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef = {
    val actor = actorName match {
      case Some(name) => system.actorOf(props, name)
      case None => system.actorOf(props)
    }
    awaitActorRef(actor, system)
  }

  // Will wait until an actor has come up before returning its ActorRef
  def awaitActorRef(actor: ActorRef, system: ActorSystem)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef = {
    Await.result(system.actorSelection(actor.path).resolveOne(), timeout.duration)
    actor
  }
}

trait ActorWaitHelper { this: Actor =>
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props, actorName: Option[String] = None)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef =
    ActorWaitHelper.awaitActor(props, context.system, actorName)(timeout)
}
