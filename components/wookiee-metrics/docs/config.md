# Wookiee - Metrics Config

Entry name for config is "wookiee-metrics". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.metrics.MetricsManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| application-name | Webtrends Harness | application name used for persisting metrics |
| metric-prefix | workstations | prefix to append metrics being sent to graphite |
| jmx.enabled | true | |
| jmx.port | 9999 | |
| graphite.enabled | false | |
| graphite.host | "" | |
| graphite.port | 2003 | |
| graphite.interval | 5 | |
| graphite.vmmetrics | true | |
| graphite.regex | "" | |

### Config supplied in JAR

````
wookiee-metrics {
  manager = "com.webtrends.harness.component.metrics.MetricsManager"
  enabled = true
  dynamic-component = true

  # This section is for monitoring the application

  # What is the application name (used for persisting metrics)
  application-name = "Webtrends Harness"
  # the prefix to append metrics being sent to graphite
  metric-prefix = workstations

  # jmx settings
  jmx {
    # Is the application reporting through the JMX interface
    enabled = true
    # If JMX is enabled then which port is it running on
    port = 9999
  }

  # graphite settings
  graphite {
    # Should the application pump metrics directly to graphite
    enabled = false
    # What is the fqdn for the graphite server
    host = ""
    # What port is graphite listening on
    port = 2003
    # How often (minutes) should we flush metrics to graphite
    interval = 5
    # Should we include the JVM metrics when sending to graphite
    vmmetrics = true
    # This is a regular expression for which metrics should be sent on to graphite. All metrics are still exposed via JMX or the metrics endpoint
    regex = ""
  }
}
```

