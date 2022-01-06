package com.oracle.infy.wookiee.test.config

import com.typesafe.config.{Config, ConfigFactory}

// This is a test configuration for you to add any additional configuration information
object TestConfig {

  def conf: Config = conf(ConfigFactory.parseString(""))

  def conf(confStr: String): Config = conf(ConfigFactory.parseString(confStr))

  def conf(config: Config): Config = config.withFallback(ConfigFactory.load()).resolve
}
