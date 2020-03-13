package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import com.webtrends.harness.service.test.policy.WeatherForcast

import scala.concurrent.{Future, Promise}

/**
 * Example Command
 */
class WeatherCommand extends Command with WeatherForcast {

  override type CommandBeanType = WeatherData
  override type CommandResponseType = String

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   */
  override def execute[T: Manifest](bean: CommandBean[CommandBeanType]): Future[CommandResponse[CommandResponseType]] = {
    val p = Promise[CommandResponse[CommandResponseType]]
    p success CommandResponse(Some(handle(bean.data.name, bean.data.location,  bean.data.forecastOption)))
    p.future
  }

  def handle(name: String, location: String, forecastOption: String) = {
    current(location)
  }
}
