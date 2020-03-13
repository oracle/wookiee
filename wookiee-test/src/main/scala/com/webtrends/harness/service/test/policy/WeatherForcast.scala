package com.webtrends.harness.service.test.policy

import com.webtrends.harness.service.test.service.DarkSkyAPI

trait WeatherForcast {

  def current(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // parse it for current
  }

  def tenDayForecast(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // parse it for current
  }


  def alerts(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // parse it for alerts
  }

}
