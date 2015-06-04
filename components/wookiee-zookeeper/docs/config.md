# Wookiee - Component: Zookeeper Config

Entry name for config is "wookiee-zookeeper". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.zookeeper.ZookeeperManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| datacenter | Lab | data center to point to |
| pod | H | environment within that center |
| quorum | "" | Required field and would be the list of fqdn to the zookeeper quorum |
| session-timeout | 30s | The zookeeper session timeout. Defaults to 30 seconds. |
| connection-timeout | 30s | The alloted time to try an connect to zookeeper. Defaults to 30 seconds. |
| retry-sleep | 5s | The alloted time to sleep before trying to connect to zookeeper. Defaults to 5 seconds. |
| retry-count | 150 | The number of times to retry to connect to zookeeper. Defaults to 150. |
| base-path | /discovery/clusters | If using clustering as well needs to be the same as the base path in the clusters config discovery.cluster.base-path |

### Config supplied in JAR

````
wookiee-zookeeper {
  manager = "com.webtrends.harness.component.zookeeper.ZookeeperManager"
  enabled = true
  dynamic-component = true

  # The data center to point to
  datacenter = "Lab"
  # The environment within the center
  pod = "H"
  # The list of fqdn to the zookeeper quorom. Example: hzoo01.staging.dmz,hzoo02.staging.dmz,hzoo03.staging.dmz.
  #required field
  #quorum = ""
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

```