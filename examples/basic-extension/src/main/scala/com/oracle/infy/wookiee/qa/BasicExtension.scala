package com.oracle.infy.wookiee.qa

import com.oracle.infy.wookiee.component.Extension
import com.oracle.infy.wookiee.qa.duplicate.DuplicateClass

class BasicExtension(name: String) extends Extension(name) {
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
