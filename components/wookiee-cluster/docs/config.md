# Wookiee - Cluster Config

Entry name for config is "wookiee-cluster". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.cluster.ClusterManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| base-path | /discovery/clusters | |
| random-seed-nodes | 3 | |
| persist-leader | off | |
| jmx.enabled | off | |
| log-info | on | |
| auto-down-unreachable-after | 10s | |
| use-dispatcher | akka.cluster.cluster-dispatcher | |
| metrics.enabled | off | |
| failure-detector.threshold | 10 | |
| failure-detector.heartbeat-interval | 1s | |
| failure-detector.acceptable-heartbeat-pause | 5s | |
| cluster-dispatcher.type | Dispatcher | |
| cluster-dispatcher.executor | fork-join-executor | |
| cluster-dispatcher.fork-join-executor.parallelism-min | 2 | |
| cluster-dispatcher.fork-join-executor.parallelism-max | 4 | |
| wookiee-zookeeper | N/A | [See wookiee-zookeeper config docs](../../components/wookiee-zookeeper/docs/config.md) |
| akka.actor.provider | akka.cluster.ClusterActorRefProvider | |


### Config supplied in JAR

````
wookiee-cluster {
  manager = "com.webtrends.harness.component.cluster.ClusterManager"
  enabled = true
  dynamic-component = true
  # See the documentation here: http://doc.akka.io/docs/akka/2.2.0/general/configuration.html#akka-cluster
  base-path = "/discovery/clusters"
  random-seed-nodes = 3
  persist-leader = off

  jmx.enabled = off

  # Enable/disable info level logging of cluster events
  log-info = on
  auto-down-unreachable-after = 10s

  # Utilize a dispatcher specific for cluster events
  use-dispatcher = "akka.cluster.cluster-dispatcher"
  metrics {
    enabled = off
  }
  failure-detector {
    threshold = 10
    heartbeat-interval = 1s
    acceptable-heartbeat-pause = 5s
  }
  cluster-dispatcher {
    type = "Dispatcher"
    executor = "fork-join-executor"
    fork-join-executor {
      parallelism-min = 2
      parallelism-max = 4
    }
  }

  wookiee-zookeeper {
    # The data center to point to
    datacenter = "Lab"
    # The environment within the center
    pod = "H"
    # The list of fqdn to the zookeeper quorom. Example: hzoo01.staging.dmz,hzoo02.staging.dmz,hzoo03.staging.dmz.
    # this field is required
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
}
akka {
  actor {
    provider = "akka.cluster.ClusterActorRefProvider"
  }
}
```