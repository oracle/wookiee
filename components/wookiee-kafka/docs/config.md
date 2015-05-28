# Wookiee - Kafka Config

Entry name for config is "wookiee-kafka". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.kafka.WorkerManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| topics | | |
| worker-class | com.webtrends.harness.component.kafka.WorkerManager | |

### Config supplied in JAR

```
wookiee-kafka {
	manager = "com.webtrends.harness.component.kafka.WorkerManager"
	enabled = true
	dynamic-component = true

	topics = [
		{
			name = "Lab_N_lrRawHits"
			event-age-threshold-seconds = 9999
		}
	]
	worker-class = "com.webtrends.harness.component.kafka.actor.Worker"
}

wookiee-zookeeper {
	datacenter = "Lab"
	pod = "Tests"
  #this field is required
	#quorum = ""
	session-timeout = 30s
	connection-timeout = 30s
	retry-sleep = 5s
	retry-count = 150
	base-path = "/discovery/clusters"
	message-processor {
		# How often the MessageProcessor should share it's subscription information
		share-interval = 1s
		# When should MessageTopicProcessor instances be removed after there are no longer any subscribers for that topic
		trash-interval = 30s
		# The default send timeout
		default-send-timeout = 2s
	}
}

commands {
    # generally this should be enabled
    enabled = true
    default-nr-routees = 5
}

message-processor {
	# How often the MessageProcessor should share it's subscription information
	share-interval = 1s
	# When should MessageTopicProcessor instances be removed after there are no longer any subscribers for that topic
	trash-interval = 30s
	# The default send timeout
	default-send-timeout = 2s
}
```