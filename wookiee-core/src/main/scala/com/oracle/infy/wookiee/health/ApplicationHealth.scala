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
package com.oracle.infy.wookiee.health

import ComponentState.ComponentState
import com.oracle.infy.wookiee.utils.{Json, JsonSerializable}
import org.joda.time.DateTime

import scala.collection.immutable.ListMap

case class ApplicationHealth(
    applicationName: String,
    version: String,
    time: DateTime,
    state: ComponentState,
    details: String,
    components: Seq[HealthComponent]
) extends JsonSerializable {

  override def toJson: String = {
    val props = ListMap[String, Any](
      "applicationName" -> applicationName,
      "startedTime" -> time,
      "version" -> version,
      "state" -> state.toString,
      "details" -> details,
      "components" -> components
    )
    Json.build(props, sort = false).toString
  }
}

case class ComponentHealth(state: ComponentState, details: String)
