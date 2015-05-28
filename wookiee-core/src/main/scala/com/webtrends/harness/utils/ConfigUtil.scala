/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.utils

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.typesafe.config.{ConfigException, Config, ConfigFactory}

object ConfigUtil {

  lazy val referenceConfig = ConfigFactory.defaultReference

  /**
   * Gets a sub config based on the current config
   *
   * @param config root config
   * @param path path where the sub config resides
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
   * @param path path to retrieve the value from
   * @param f function to execute to retrieve the value
   * @param default the default value you want set if is not available
   * @tparam T The type for the expected return object
   * @return Option value with expected type
   */
  def getDefaultValue[T](path:String, f: (String) => T, default:T) : T = {
    try {
      f(path)
    } catch {
      case c:ConfigException => default
    }
  }

  /**
   * Gets the default timeout from a config
   *
   * @param config config containing the timeout
   * @param path path to the timeout
   * @param unit TimeUnit
   * @param default default if not found in config
   * @return Option value with Timeout
   */
  def getDefaultTimeout(config:Config, path:String, default:Timeout, unit:TimeUnit=TimeUnit.SECONDS) : Timeout = {
    if (config.hasPath(path)) {
      val duration = config.getDuration(path, unit)
      Timeout(duration, unit)
    } else {
      default
    }
  }
}