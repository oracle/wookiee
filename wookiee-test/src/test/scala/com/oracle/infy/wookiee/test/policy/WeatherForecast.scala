package com.oracle.infy.wookiee.test.policy

import com.oracle.infy.wookiee.test.service.DarkSkyAPI

trait WeatherForecast {

  def current(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // include a json library, then parse this for current
  }

  def tenDayForecast(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // include a json library, then parse this for current
  }


  def alerts(location:String): String = {
    val data = DarkSkyAPI.getWeather(location)
    data // include a json library, then parse this for current
  }

}
