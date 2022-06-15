package com.oracle.infy.wookiee.metrics.model

import com.codahale.metrics.MetricRegistry

final case class WookieeRegistry(metricRegistry: MetricRegistry, jvmRegistry: MetricRegistry)
