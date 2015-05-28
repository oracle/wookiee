# Wookiee - Component: Memcache Config

Entry name for config is "wookiee-memcache-cache". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.memcache.MemcacheManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |

### Config supplied in JAR

````
wookiee-cache-memcache {
  manager = "com.webtrends.harness.component.memcache.MemcacheManager"
  enabled = true
  dynamic-component = true
}
```