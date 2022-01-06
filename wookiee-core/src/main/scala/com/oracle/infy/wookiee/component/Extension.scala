package com.oracle.infy.wookiee.component

abstract class Extension(name: String) extends Component(name) {

  def initialize(): Unit = {}

  override def systemReady(): Unit = {
    initialize()
    super.systemReady()
  }

}
