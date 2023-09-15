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

package com.oracle.infy.wookiee.config

import akka.actor.Actor
import com.oracle.infy.wookiee.app.HarnessActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.oracle.infy.wookiee.app.HarnessActor.ConfigChange
import com.oracle.infy.wookiee.health.WookieeMonitor

trait ConfigHelper extends ConfigHelperV2 {
  this: Actor =>

  override var renewableConfig: Config = context.system.settings.config

  def configReceive: Receive = {
    case ConfigChange() =>
      propagateRenewConfiguration()
  }
}
