package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.app.HarnessActorSystem
import com.typesafe.config.Config

abstract class Extension(name: String) extends Component(name) {
  log.info(s"Starting Extension Manager for '$name', reading current configs..")
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
