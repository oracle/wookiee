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
package com.oracle.infy.wookiee

import com.oracle.infy.wookiee.health.Health

object HarnessConstants {
  val ActorPrefix = "/user/system"
  // actor names
  val ServicesName = "services"
  val ComponentName = "component"
  val CommandName = "command"
  val TypedCommandName = "typedCommand"
  val ComponentReloadName = "component-reloader"

  // full actor names
  val ServicesFullName: String = ActorPrefix + "/" + ServicesName
  val HealthFullName: String = ActorPrefix + "/" + Health.HealthName
  val ComponentFullName: String = ActorPrefix + "/" + ComponentName
  val CommandFullName: String = ActorPrefix + "/" + CommandName

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
  val KeyDynamicLoading = "components.dynamic-loading"

  // constants for services
  val KeyServicePath = "services.path"
  val KeyInternalService = "services.internal"
  val KeyInternalServiceConfig = "services.internal-config"
  val KeyServiceCheckTimeout = "services.check-timeout"
  val KeyServiceDistinctClassLoader = "services.distinct-classloader"
  val KeyServiceClassLoaderName = "service-classloader"

  val LogHealthCheckDiffs = "logging.health-check.diff-compare"
}
