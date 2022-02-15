/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 9:31 PM
 */
package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.command.{Command, CommandResponse, CommandBean}
import scala.concurrent.Future

class ${service-name}Command extends Command {
  import context.dispatcher

  /**
  * Name of the command that will be used for the actor name
  *
  * @return
  */
  override def commandName: String = ${service-name}Command.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future {
      CommandResponse(Some("TODO: All execute work here".asInstanceOf[T]))
    }
  }
}

object ${service-name}Command {
  def CommandName = "${service-name}"
}