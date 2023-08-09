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

  /* Java Interop */

  override def canEqual(that: Any): Boolean = that match {
    case _: ComponentV2 => true
    case _              => false
  }

  override def productElement(n: Int): Any = {
    n match {
      case 0 => name
      case 1 => config
      case _ => throw new IndexOutOfBoundsException(n.toString)
    }
  }

  override def productArity: Int = 2
}
