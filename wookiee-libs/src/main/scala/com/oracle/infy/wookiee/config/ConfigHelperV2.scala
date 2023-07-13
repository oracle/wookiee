package com.oracle.infy.wookiee.config

import com.oracle.infy.wookiee.app.WookieeSupervisor
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.typesafe.config.{Config, ConfigFactory}

/**
  * This helper class will keep track of a configuration local to your service/component and update
  * the configuration whenever ConfigWatcher detects changes in any of our config files.
  *
  * @author Spencer Wood
  */
trait ConfigHelperV2 extends WookieeMonitor {
  var renewableConfig: Config

  /**
    * Should override this method and use it when ConfigChange is received, be sure
    * to call super.renewConfiguration() first though to ensure you get the new values
    */
  def renewConfiguration(): Unit = {
    ConfigFactory.invalidateCaches()
    renewableConfig = WookieeSupervisor.renewConfigsAndClasses(None)
  }

  def propagateRenewConfiguration(): Unit = {
    propagateCall({ case x: ConfigHelperV2 => x.renewConfiguration() }, "WCH400: Failed to renew configuration")
  }
}
