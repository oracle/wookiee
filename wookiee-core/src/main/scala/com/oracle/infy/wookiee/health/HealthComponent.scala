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

/**
 * This is the current state of the component. A 'Normal' state refers to
 * the system operating as it should and without any issues. A 'Degraded' state
 * refers to the system operating with difficulty. Notification of the state should
 * be made, however, this state does not mean that the service should be removed from
 * service. A 'Critical' state refers to the system operating in a fully compromised
 * state. Notification of the state should occur and the service should be taken out
 * of service until the issue is resolved.
 */
object ComponentState extends Enumeration {
  type ComponentState = Value
  val NORMAL, DEGRADED, CRITICAL = Value
}

import ComponentState._
import com.oracle.infy.wookiee.utils.{Json, JsonSerializable}

import scala.collection.immutable.ListMap

/**
 * The health component is used to define a subset of functionality that
 * can be asked of it's state. It can contain optional child components.
 * @param name  The name of the component being checked
 * @param state The current state of the component (defaults to NORMAL)
 * @param extra Any extra information
 */
case class HealthComponent(name: String,
                           state: ComponentState = ComponentState.NORMAL,
                           details: String,
                           extra: Option[AnyRef] = None,
                           var components: List[HealthComponent] = List.empty) extends JsonSerializable {
  override def toJson(): String = {
    val extraDetails = extra match {
      case Some(s) => s.toString
      case None => ""
    }

    val props = ListMap[String, Any](
      "name" -> name,
      "state" -> state.toString,
      "details" -> details,
      "extra" -> extraDetails,
      "components" -> components
    )
    Json.build(props, sort = false).toString
  }

  def addComponent(component: HealthComponent): Unit = components = components :+ component
}
