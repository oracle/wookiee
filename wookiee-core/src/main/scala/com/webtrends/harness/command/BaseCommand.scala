package com.webtrends.harness.command

import com.webtrends.harness.logging.Logger

import scala.concurrent.Future

trait BaseCommand {
  protected val log : Logger
  def path: String
  def execute[T:Manifest](bean: Option[CommandBean]) : Future[BaseCommandResponse[T]]
}
