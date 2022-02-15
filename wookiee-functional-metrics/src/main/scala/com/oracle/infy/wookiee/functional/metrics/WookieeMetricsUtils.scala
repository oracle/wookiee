package com.oracle.infy.wookiee.functional.metrics

import java.net.{InetAddress, InetSocketAddress}

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.jmx.JmxReporter

object WookieeMetricsUtils {

  def graphiteReporter(
      registry: MetricRegistry,
      metricPrefix: String,
      applicationName: String,
      graphiteHost: String,
      graphitePort: Int
  ): GraphiteReporter =
    GraphiteReporter
      .forRegistry(registry)
      .prefixedWith(
        "%s.%s.%s".format(
          metricPrefix,
          InetAddress.getLocalHost.getHostName.replace('.', '_'),
          applicationName.replace(' ', '_').toLowerCase
        )
      )
      .build(new Graphite(new InetSocketAddress(graphiteHost, graphitePort)))

  def jmxReporter(registry: MetricRegistry): JmxReporter = JmxReporter.forRegistry(registry).build

}
