package com.oracle.infy.wookiee.discovery.command

import com.oracle.infy.wookiee.component.ComponentInfo
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import com.oracle.infy.wookiee.service.ServiceV2
import com.oracle.infy.wookiee.utils.ThreadUtil

// Useful trait for creating a Wookiee Service that hosts discoverable commands
abstract class WookieeDiscoverableService(config: Config)
    extends ServiceV2(config)
    with DiscoverableCommandExecution
    with DiscoverableCommandHelper {
  // Generally this will contain calls to DiscoverableCommandHelper.registerDiscoverableCommand
  def addDiscoverableCommands(implicit conf: Config, ec: ExecutionContext): Unit

  // Will kick off the addDiscoverableCommands method once wookiee-grpc component is ready
  // Be sure to call super.onComponentReady(info) if you override this method
  // Should contain a number of calls to registerDiscoverableCommand which is exposed by this trait
  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    if (info.name == GrpcManager.ComponentName) {
      addDiscoverableCommands(config, ThreadUtil.createEC(s"discoverable-$name}"))
    }
  }
}
