package com.oracle.infy.wookiee.test.command

import com.oracle.infy.wookiee.command.Command

import scala.concurrent.Future

case class WeatherData(name: String, location: String, mode: String = "current")

/**
  * Example Command
  */
class WeatherCommand extends Command[WeatherData, String] {

  /**
    * The primary entry point for the command, the actor for this command
    * will ignore all other messaging and only execute through this
    */
  override def execute(bean: WeatherData): Future[String] = {
    Future.successful(s"Weather: ${bean.location}|${bean.mode}")
  }
}
