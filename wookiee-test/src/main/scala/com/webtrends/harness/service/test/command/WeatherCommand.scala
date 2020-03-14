package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.{Command, CommandHelper}
import com.webtrends.harness.service.test.policy.WeatherForcast

import scala.concurrent.{Future, Promise}


/**
 * Example Command
 */
class WeatherCommand extends Command[WeatherData, String] with WeatherForcast {


  /**
   * Sets the default command name to the simple classname
   */
  override def commandName: String = WeatherCommand.CommandName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   */
  override def execute(bean: WeatherData): Future[String] = {
    val p = Promise[String]
    p success handle(bean.name, bean.location,  bean.mode)
    p.future
  }


  def handle(name: String, location: String, mode: String) = {
    current(location)
  }
}

object WeatherCommand {
  def CommandName = "weather"
}