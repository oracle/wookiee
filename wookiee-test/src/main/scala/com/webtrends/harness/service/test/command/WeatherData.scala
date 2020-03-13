package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.CommandBeanData


case class WeatherData(name: String, location: String, forecastOption:String) extends CommandBeanData
