package com.oracle.infy.wookiee.component

import com.typesafe.config.Config

case class ComponentV2(override val name: String, config: Config) extends WookieeComponent {

  /**
    * Starts the component, called right away
    */
  override def start(): Unit = {}
}
