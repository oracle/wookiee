package com.webtrends.infy.qa

import com.webtrends.harness.component.Extension
import other.`package`.DuplicateClass

class OtherExtension(name: String) extends Extension(name) {
  private val pkg = 'O'

  override def initialize(): Unit = {
    log.info(s"Component Package: '$pkg'")
    log.info(s"Extension: $name")
    DuplicateClass.logInfo(name)
    super.initialize()
  }

  override def start(): Unit = {
    super.start
    initialize()
  }

  override def receive: Receive = super.receive orElse {
    case str: String if str == "log" => sender ! DuplicateClass.logInfo(name)
  }
}
