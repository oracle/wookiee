package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.component.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.utils.ClassUtil

trait ComponentInfo {
  val name: String
  val state: ComponentState
}
case class ComponentReady(info: ComponentInfo)

object ComponentState extends Enumeration {
  type ComponentState = Value
  val Initializing, Started, Failed = Value
}

case class ComponentInfoV2(name: String, state: ComponentState, component: ComponentV2) extends ComponentInfo {

  override def toString: String =
    s"ComponentInfoV2($name, $state, ${ClassUtil.getSimpleNameSafe(component.getClass)})"
}

trait WookieeComponent extends WookieeMonitor {
  val name: String
}
