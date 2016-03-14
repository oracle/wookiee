package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.{CommandResponse, CommandBean, Command}

import scala.concurrent.{Future, Promise}

/**
 * Created by crossleyp on 8/19/15.
 */
class TestCommand extends Command {
  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = TestCommand.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  override def execute[T:Manifest](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    val p = Promise[CommandResponse[T]]
    p success CommandResponse[T](Some("Test OK".asInstanceOf[T]), "plain/text")
    p.future
  }
}

object TestCommand {
  def CommandName:String = "TestCommand"
}
