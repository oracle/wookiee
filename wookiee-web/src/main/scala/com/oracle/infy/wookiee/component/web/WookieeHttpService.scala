package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.component.ComponentInfo
import com.oracle.infy.wookiee.service.ServiceV2
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

// Useful trait for creating a Wookiee Service that uses Helidon endpoints
abstract class WookieeHttpService(config: Config) extends ServiceV2(config) {
  // Should mainly contain calls to HelidonManager.registerEndpoint
  def addCommands(implicit conf: Config, ec: ExecutionContext): Unit

  // Will kick off the addCommands method once wookiee-web component is ready
  // Be sure to call super.onComponentReady(info) if you override this method
  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    if (info.name == WebManager.ComponentName) {
      addCommands(config, ThreadUtil.createEC(s"helidon-$name"))
    }
  }
}
