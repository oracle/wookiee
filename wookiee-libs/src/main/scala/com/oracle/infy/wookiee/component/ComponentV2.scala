package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.command.WookieeCommandHelper
import com.oracle.infy.wookiee.config.ConfigHelperV2
import com.typesafe.config.Config

case class ComponentV2(override val name: String, override val config: Config)
    extends WookieeComponent
    with WookieeCommandHelper
    with WookieeActor
    with ConfigHelperV2 {
  // Use this config if you expect the watched config file to change
  override var renewableConfig: Config = config

  // When someone sends a ComponentRequest to this component, this method will be called
  // It is acceptable to return a Future[Any] from this method
  def onRequest[T](msg: T): Any = self ? msg

  // When someone sends a ComponentMessage to this component, this method will be called
  // Nothing will wait on a result from this method
  def onMessage[T](msg: T): Unit = self ! msg
}
