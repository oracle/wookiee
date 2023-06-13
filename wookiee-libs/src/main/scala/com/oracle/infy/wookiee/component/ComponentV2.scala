package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.command.WookieeCommandHelper
import com.typesafe.config.Config

case class ComponentV2(override val name: String, override val config: Config)
    extends WookieeComponent
    with WookieeCommandHelper {
  // When someone sends a ComponentRequest to this component, this method will be called
  // It is acceptable to return a Future[Any] from this method
  def onRequest[T](msg: T): Any = ???

  // When someone sends a ComponentMessage to this component, this method will be called
  // Nothing will wait on a result from this method
  def onMessage[T](msg: T): Unit = ???
}
