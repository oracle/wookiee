package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.Command
import com.webtrends.harness.service.test.policy.WeatherForecast

import scala.concurrent.{Future, Promise}

case class WeatherData(name: String, location: String, mode:String = "current")

/**
 * Example Command
 */
class WeatherCommand extends Command[WeatherData, String] with WeatherForecast {


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

  def handle(name: String, location: String, mode: String): String = {
    mode match {
      case "forecast" => tenDayForecast(location)
      case "alerts" => alerts(location)
      case _ => current(location)
    }
  }
}

object WeatherCommand {
  def CommandName = "weather"
}