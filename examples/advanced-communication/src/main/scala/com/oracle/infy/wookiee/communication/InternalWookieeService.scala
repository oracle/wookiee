package com.oracle.infy.wookiee.communication

import com.oracle.infy.wookiee.communication.command.{InternalDiscoverableCommand, InternalKafkaCommand}
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.InputHolder
import com.oracle.infy.wookiee.communication.command.InternalKafkaCommand.TopicInfo
import com.oracle.infy.wookiee.discovery.command.{DiscoverableCommandHelper, WookieeDiscoverableService}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class InternalWookieeService(config: Config) extends WookieeDiscoverableService(config) {
  override val name: String = "Internal Wookiee Service"

  override def addDiscoverableCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    // This command will now be discoverable by other services on the configured 'zk-discovery-path'
    DiscoverableCommandHelper.registerDiscoverableCommand[InputHolder](
      new InternalDiscoverableCommand
    )
    DiscoverableCommandHelper.registerDiscoverableCommand[TopicInfo](
      new InternalKafkaCommand(config),
      // If you'd like bearer token auth, put it here
      Some(InternalDiscoverableCommand.getInternalConfig(config, "bearer-token")),
      List().asJava // Custom intercepts would go here
    )
  }
}
