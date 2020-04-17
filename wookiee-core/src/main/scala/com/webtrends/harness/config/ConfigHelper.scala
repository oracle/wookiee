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

package com.webtrends.harness.config

import akka.actor.Actor
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.app.HarnessActor.ConfigChange
import com.webtrends.harness.app.HarnessActorSystem

/**
 * This helper class will keep track of a configuration local to your service/component and update
 * the configuration whenever ConfigWatcherActor detects changes in any of our config files.
 *
 * @author woods
 */
trait ConfigHelper {
  this: Actor =>

  var renewableConfig: Config = context.system.settings.config

  /**
   * Should override this method and use it when ConfigChange is received, be sure
   * to call super.renewConfiguration() though to ensure you get the new values
   */
  def renewConfiguration() {
    ConfigFactory.invalidateCaches()
    renewableConfig = HarnessActorSystem.getConfig(None, None)
  }

  def configReceive: Receive = {
    case ConfigChange() =>
      renewConfiguration()
  }
}
