package com.oracle.infy.wookiee.component

import com.typesafe.config.Config

abstract class ExtensionV2(name: String, config: Config) extends ComponentV2(name, config) {
  log.info(s"Starting Extension Manager for '$name', reading current configs..")
  // Use this config instead of 'config'
  // it will update after a hot/dynamic ExtensionV2 reload
  def dynamicConfig: Config = config

  def initialize(): Unit = {}

  override def systemReady(): Unit = {
    initialize()
    super.systemReady()
  }
}
