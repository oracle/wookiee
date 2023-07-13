package com.oracle.infy.wookiee.qa

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.component.ExtensionV2
import com.oracle.infy.wookiee.qa.duplicate.DuplicateClass
import com.typesafe.config.Config

// Extending Mediator allows us to statically store the instance of BasicExtension
object BasicExtension extends Mediator[BasicExtension]

class BasicExtension(name: String, config: Config) extends ExtensionV2(name, config) {
  BasicExtension.registerMediator(config, this)
  private val pkg = 'A'

  override def initialize(): Unit = {
    log.info(s"Component Package: '$pkg'")
    log.info(s"Extension: $name")
    DuplicateClass.logInfo(name)
    super.initialize()
  }

  override def start(): Unit = {
    super.start()
    initialize()
  }

  override def receive: Receive = super.receive orElse {
    case str: String if str == "log" => sender() ! DuplicateClass.logInfo(name)
  }
}
