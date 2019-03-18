package com.webtrends.harness.command

import scala.concurrent.Future

trait BaseCommand {
  def path: String
  def execute[T:Manifest](bean: Option[CommandBean]) : Future[BaseCommandResponse[T]]
}
