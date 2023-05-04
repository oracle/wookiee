package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.component.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.WookieeMonitor

trait ComponentInfo {
  val name: String
  val state: ComponentState
}
case class ComponentReady(info: ComponentInfo)

object ComponentState extends Enumeration {
  type ComponentState = Value
  val Initializing, Started, Failed = Value
}
case class ComponentInfoV2(name: String, state: ComponentState, component: ComponentV2) extends ComponentInfo

trait WookieeComponent extends WookieeMonitor {
  val name: String
}
