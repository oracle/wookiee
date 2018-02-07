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
package com.webtrends.harness

import com.webtrends.harness.health.Health

object HarnessConstants {
  val ActorPrefix = "/user/system"
  // actor names
  val ServicesName = "services"
  val ComponentName = "component"
  val CommandName = "command"
  val TypedCommandName = "typedCommand"
  val PolicyName = "policy"
  // full actor names
  val ServicesFullName = ActorPrefix + "/" + ServicesName
  val HealthFullName = ActorPrefix + "/" + Health.HealthName
  val ComponentFullName = ActorPrefix + "/" + ComponentName
  val CommandFullName = ActorPrefix + "/" + CommandName
  val TypedCommandFullName = ActorPrefix + "/" + TypedCommandName
  val PolicyFullName = ActorPrefix + "/" + PolicyName

  val KeyStartupTimeout = "startup-timeout"
  val KeyDefaultTimeout = "default-timeout"
  val KeyCommandsEnabled = "commands.enabled"
  val KeyCommandsNrRoutees = "commands.default-nr-routees"
  val TestMode = "test-mode"
  val PrepareToShutdownTimeout = "prepare-to-shutdown-timeout"

  val KeyInternalHttpPort = "internal-http.port"
  val KeyInternalHttpEnabled = "internal-http.enabled"

  // constants for components
  val KeyPathComponents = "components.path"
  val KeyComponentMapping = "components.mappings"
  val KeyComponents = "components.lib-components"
  val KeyComponentStartTimeout = "components.start-timeout"
  val KeyDynamicComponent = "dynamic-component"

  // constants for services
  val KeyServicePath = "services.path"
  val KeyInternalService = "services.internal"
  val KeyInternalServiceConfig = "services.internal-config"
  val KeyServiceCheckTimeout = "services.check-timeout"
  val KeyServiceDistinctClassLoader = "services.distinct-classloader"
}
