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
package com.webtrends.harness.component.metrics.monitoring

import com.typesafe.config.{ConfigFactory, Config}

class MonitoringSettings (config: Config = ConfigFactory.load) {

    // What is the application name (used for persisting metrics)
    var ApplicationName         = config getString       "application-name"
    //Is the application reporting through the JMX interface
    var JmxEnabled              = config getBoolean      "jmx.enabled"
    // If JMX is enabled then which port is it running on
    var JmxPort                 = config getInt          "jmx.port"
    // The prefix to append metrics being sent to graphite
    var MetricPrefix            = config getString       "metric-prefix"
    // Should the application pump metrics directly to graphite
    var GraphiteEnabled         = config getBoolean      "graphite.enabled"
    // What is the fqdn for the graphite server
    var GraphiteHost            = config getString       "graphite.host"
    // What port is graphite listening on
    var GraphitePort            = config getInt          "graphite.port"
    // How often (minutes) should we flush metrics to graphite
    var GraphiteInterval        = config getInt          "graphite.interval"
    // Should we include the JVM metrics when sending to graphite
    var GraphiteIncludeVMMetrics = config getBoolean      "graphite.vmmetrics"
    // This is a regular expression for which metrics should be sent on to graphite. All metrics are still exposed via JMX or the metrics endpoint
    var GraphiteRegEx           = config getString       "graphite.regex"

    require(ApplicationName   != "", "application-name must be set")
    require(MetricPrefix      != "", "metric-prefix must be set")
}

object MonitoringSettings {
    implicit def apply(config: Config = ConfigFactory.load()) : MonitoringSettings = new MonitoringSettings(config)
}
