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
package com.webtrends.harness.monitoring

import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.metrics.monitoring.MonitoringSettings
import com.webtrends.harness.utils.ConfigUtil
import org.specs2.mutable.SpecificationWithJUnit

class MonitoringSettingsSpec extends SpecificationWithJUnit {

  "MonitoringSettings" should {
    "load properly from the reference file reference.conf" in {

      val settings = MonitoringSettings(ConfigUtil.prepareSubConfig(ConfigFactory.load("reference.conf"), "wookiee-metrics"))
      settings.ApplicationName must not beEmpty
    }

    "load properly from parsing a configuration string" in {
      val settings = MonitoringSettings(ConfigUtil.prepareSubConfig(ConfigFactory.parseString( """
        wookiee-metrics {
           application-name = "Webtrends Harness"
           metric-prefix = workstations
           jmx {
             enabled = false
             port = 9999
           }
           graphite {
             enabled = false
             host = ""
             port = 2003
             interval = 5
             vmmetrics=true
             regex=""
           }
         }
        """), "wookiee-metrics"))

      settings.ApplicationName must not beEmpty

    }

    "throw an error with an invalid configuration" in {
      val config = ConfigUtil.prepareSubConfig(ConfigFactory.parseString( """
        wookiee-metrics {
           application-name = ""
           metric-prefix = workstations
           jmx {
             enabled = false
             port = 9999
           }
           graphite {
             enabled = false
             host = ""
             port = 2003
             interval = 5
             vmmetrics=true
             regex=""
           }
         }
         """), "wookiee-metrics")

      MonitoringSettings(config) must throwA[IllegalArgumentException]
    }
  }
}
