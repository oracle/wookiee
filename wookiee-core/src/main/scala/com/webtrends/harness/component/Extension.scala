package com.webtrends.harness.component

abstract class Extension(name: String) extends Component(name) {

  def initialize(): Unit = {}

  def addEndpoints(): Unit

  override def systemReady(): Unit = {
    initialize()
    addEndpoints()
    super.systemReady()
  }

}
