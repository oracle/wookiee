package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.config.ConfigHelperV2
import com.typesafe.config.Config

abstract case class ServiceV2(config: Config) extends WookieeActor with WookieeService with ConfigHelperV2 {
  override var renewableConfig: Config = config

  /* Java Interop */

  override def canEqual(that: Any): Boolean = that match {
    case _: ServiceV2 => true
    case _            => false
  }

  override def productElement(n: Int): Any = {
    n match {
      case 0 => config
      case _ => throw new IndexOutOfBoundsException(n.toString)
    }
  }

  override def productArity: Int = 1
}
