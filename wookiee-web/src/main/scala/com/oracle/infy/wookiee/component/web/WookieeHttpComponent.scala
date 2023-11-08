package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.component.{ComponentInfo, ExtensionV2}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

// Useful trait for creating a Wookiee Service that uses Helidon endpoints
abstract class WookieeHttpComponent(name: String, config: Config) extends ExtensionV2(name, config) {
  // Should mainly contain calls to HelidonManager.registerEndpoint
  def addCommands(implicit conf: Config, ec: ExecutionContext): Unit

  // Will kick off the addCommands method once wookiee-web component is ready
  // Be sure to call super.onComponentReady(info) if you override this method
  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    if (info.name == WebManager.ComponentName) {
      log.info(s"Adding Web Commands for [$name]..")
      addCommands(config, ThreadUtil.createEC(s"web-$name"))
    }
  }
}
