package com.oracle.infy.wookiee.metrics.model


//TODO: need to be finalized.
case class MetricsSettings(
    applicationName: String,
    metricPrefix: String,
    jmxEnabled: Boolean,
    graphiteEnabled: Boolean,
    graphiteHost: String,
    graphitePort: Int,
    graphiteInterval: Int
)
