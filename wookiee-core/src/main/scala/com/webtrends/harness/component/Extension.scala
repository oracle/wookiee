package com.webtrends.harness.component

abstract class Extension(name: String) extends Component(name) {

  def initialize(): Unit = {}

  override def systemReady(): Unit = {
    initialize()
    super.systemReady()
  }

}
