package com.oracle.infy.wookiee.command

import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.Config

trait WookieeCommandHelper extends LoggingAdapter {
  val config: Config

  def getCommandExec: WookieeCommandExecutive = WookieeCommandExecutive.getMediator(config)

  def registerCommand(command: WookieeCommand[_, _]): Unit = {
    log.info(s"Registering command: ${command.commandName}")
    getCommandExec.registerCommand(command)
  }
}
