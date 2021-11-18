package com.webtrends.harness.component

import com.typesafe.config.Config
import com.webtrends.harness.app.HarnessActorSystem

abstract class Extension(name: String) extends Component(name) {
  private val conf = HarnessActorSystem.renewConfigsAndClasses(None)
  // Use this config instead of 'context.system.settings.config'
  // it will update after a hot/dynamic Extension reload
  def dynamicConfig: Config = conf

  def initialize(): Unit = {}

  override def systemReady(): Unit = {
    initialize()
    super.systemReady()
  }
}
