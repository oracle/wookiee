package com.oracle.infy.wookiee.communication

import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.InputHolder
import com.oracle.infy.wookiee.component.discovery.command.{DiscoverableCommandHelper, WookieeDiscoverableService}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class InternalWookieeService(config: Config) extends WookieeDiscoverableService(config) {
  override val name: String = "Internal Wookiee Service"

  override def addDiscoverableCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    DiscoverableCommandHelper.registerDiscoverableCommand[InputHolder](
      new InternalDiscoverableCommand,
      Some(InternalDiscoverableCommand.getInternalConfig(config, "bearer-token")),
      List().asJava // Custom intercepts would go here
    )
  }
}
