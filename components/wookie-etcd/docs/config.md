# Wookie - Etcd Config

Entry name for config is "wookie-etcd". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.etcd.EtcdManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| etcd-endpoint | http://localhost:4001 | |

### Config supplied in JAR

```
wookie-etcd {
	manager = "com.webtrends.harness.component.etcd.EtcdManager"
	enabled = true
	dynamic-component = true
	etcd-endpoint = "http://localhost:4001"
}

```