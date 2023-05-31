package com.oracle.infy.wookiee.component.helidon.web

import com.oracle.infy.wookiee.component.ComponentInfo
import com.oracle.infy.wookiee.component.helidon.HelidonManager
import com.oracle.infy.wookiee.service.ServiceV2
import com.typesafe.config.Config

// Useful trait for creating a Wookiee service that uses Helidon endpoints
abstract class WookieeHttpService(config: Config) extends ServiceV2(config) {
  def addCommands(): Unit

  // Will kick off the addCommands method once wookiee-helidon component is ready
  // Be sure to call super.onComponentReady(info) if you override this method
  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    if (info.name == HelidonManager.ComponentName) {
      addCommands()
    }
  }
}
