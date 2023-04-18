package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.app.WookieeShutdown
import com.oracle.infy.wookiee.component.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.WookieeHealth

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

trait WookieeComponent extends WookieeHealth with WookieeShutdown {
  val name: String

  /**
    * Starts the component, called right away
    */
  def start(): Unit

  /**
    * Any logic to run once all (non-hot deployed) components are up
    */
  def systemReady(): Unit = {}

  /**
    * Can override to act when another Wookiee Component comes up. This method will be hit when each
    * other Component in the Service has Started. It will also be back-filled with any Components that
    * reached the Started state before this Component, so no worries about load order. Great for when
    * one needs to use another Component without the need for custom waiting logic, and convenient
    * since it provides the actorRef of that Started Component.
    * Note: The Component will get a Ready message for itself as well
    *
    * @param info Info about the Component that is ready for interaction, name and actor ref.
    *             Note: The 'state' will always be Started
    */
  def onComponentReady(info: ComponentInfo): Unit = {}
}
