package com.oracle.infy.wookiee.test.command

import com.oracle.infy.wookiee.command.Command
import com.oracle.infy.wookiee.test.policy.WeatherForecast

import scala.concurrent.Future

case class WeatherData(name: String, location: String, mode: String = "current")

/**
 * Example Command
 */
class WeatherCommand extends Command[WeatherData, String] with WeatherForecast {
  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   */
  override def execute(bean: WeatherData): Future[String] = {
    val result = handle(bean.name, bean.location,  bean.mode)
    Future.successful(result)
  }

  def handle(name: String, location: String, mode: String): String = {
    mode match {
      case "forecast" => tenDayForecast(location)
      case "alerts" => alerts(location)
      case _ => current(location)
    }
  }
}