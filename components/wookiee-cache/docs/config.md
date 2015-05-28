# Wookiee - Component: Caching Config

Entry name for config is "wookiee-cache". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.cache.memory.MemoryManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |

### Config supplied in JAR

```
wookiee-cache {
  manager = "com.webtrends.harness.component.cache.memory.MemoryManager"
  enabled = true
  dynamic-component = true
}
```