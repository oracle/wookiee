package com.oracle.infy.wookiee.utils

import com.typesafe.config.{Config, ConfigException, ConfigFactory}

import scala.util.Try

object ConfigUtil {

  lazy val referenceConfig: Config = ConfigFactory.defaultReference

  /**
    * Gets a sub config based on the current config
    *
    * @param config root config
    * @param path   path where the sub config resides
    * @return
    */
  def prepareSubConfig(config: Config, path: String): Config = {
    val c = config.withFallback(referenceConfig)
    c.checkValid(referenceConfig, path)
    c.getConfig(path)
  }

  /**
    * Gets a default value from the config
    *
    * @param path    path to retrieve the value from
    * @param f       function to execute to retrieve the value
    * @param default the default value you want set if is not available
    * @tparam T The type for the expected return object
    * @return Option value with expected type
    */
  def getDefaultValue[T](path: String, f: String => T, default: T): T = {
    try {
      f(path)
    } catch {
      case _: ConfigException => default
    }
  }

  // Will check at wookiee-system.{path} and {path} in case the config shows up in only one place
  def getConfigAtEitherLevel[T](configPath: String, configMethod: String => T): T = {
    Try(configMethod(s"wookiee-system.$configPath"))
      .getOrElse(configMethod(configPath))
  }
}
