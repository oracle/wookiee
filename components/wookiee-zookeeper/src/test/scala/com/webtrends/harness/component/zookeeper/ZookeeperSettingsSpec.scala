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
package com.webtrends.harness.component.zookeeper

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.utils.ConfigUtil
import org.specs2.mutable.SpecificationWithJUnit

class ZookeeperSettingsSpec extends SpecificationWithJUnit {

  "ZookeeperSettings" should {
    "load properly with valid configuration" in {

      val config = ConfigFactory.parseString(
        """
          wookiee-zookeeper {
            datacenter="Lab"
            pod="B"
            quorum="bzoo01.staging.dmz,bzoo02.staging.dmz,bzoo03.staging.dmz"
          }
        """.stripMargin).withFallback(loadConfig)
      val subConfig = ConfigUtil.prepareSubConfig(config, "wookiee-zookeeper")

      val settings = ZookeeperSettings(subConfig)
      settings.quorum must be equalTo config.getString("wookiee-zookeeper.quorum")
      30000L must be equalTo config.getDuration("wookiee-zookeeper.session-timeout", TimeUnit.MILLISECONDS)
      5000L must be equalTo config.getDuration("wookiee-zookeeper.retry-sleep", TimeUnit.MILLISECONDS)
    }

    "throw an error with an invalid datacenter configuration" in {

      val config = ConfigFactory.parseString(
        """
          wookiee-zookeeper {
            pod="foo"
            quorum="localhost"
          }
        """.stripMargin).withFallback(loadConfig)
      val subConfig = ConfigUtil.prepareSubConfig(config, "wookiee-zookeeper")

      ZookeeperSettings(subConfig) must throwA[IllegalArgumentException]
    }

    "throw an error with an invalid pod configuration" in {

      val config = ConfigFactory.parseString(
        """
          wookiee-zookeeper {
            datacenter="Lab"
            quorum="localhost"
          }
        """.stripMargin).withFallback(loadConfig)
      val subConfig = ConfigUtil.prepareSubConfig(config, "wookiee-zookeeper")

      ZookeeperSettings(subConfig) must throwA[IllegalArgumentException]
    }

    "throw an error with an invalid quorum configuration" in {

      val config = ConfigFactory.parseString(
        """
          wookiee-zookeeper {
            datacenter="Lab"
            pod="foo"
          }
        """.stripMargin).withFallback(loadConfig)
      val subConfig = ConfigUtil.prepareSubConfig(config, "wookiee-zookeeper")

      ZookeeperSettings(subConfig) must throwA[IllegalArgumentException]
    }
  }

  private def loadConfig : Config = {
    ConfigFactory.parseString(
      """
        wookiee-zookeeper {
          manager = "com.webtrends.harness.component.zookeeper"
          libsLocation = "../components/wookiee-zookeeper/target/lib"
          jarLocation = "../components/wookiee-zookeeper/target/wookiee-zookeeper-1.0-SNAPSHOT.jar"
          # The data center to point to
          datacenter = ""
          # The environment within the center
          pod = ""
          # The list of fqdn to the zookeeper quorom. Example: hzoo01.staging.dmz,hzoo02.staging.dmz,hzoo03.staging.dmz.
          quorum = ""
          # The zookeeper session timeout. Defaults to 30 seconds.
          session-timeout = 30s
          # The alloted time to try an connect to zookeeper. Defaults to 30 seconds.
          connection-timeout = 30s
          # The alloted time to sleep before trying to connect to zookeeper. Defaults to 5 seconds.
          retry-sleep = 5s
          # The number of times to retry to connect to zookeeper. Defaults to 150.
          retry-count = 150
          # If using clustering as well needs to be the same as the base path in the clusters config discovery.cluster.base-path
          base-path = "/discovery/clusters"
        }
      """.stripMargin)
  }

}
