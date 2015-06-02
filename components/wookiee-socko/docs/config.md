# Wookiee - Component: Socko Config

Entry name for config is "wookiee-socko". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.socko.SockoManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| server-name | Webtrends Harness | |
| hostname | localhost | |
| port | 8080 | |
| num-handler-routees | 5 | number of actors handling the socko requests coming in |
| static-content.rootPaths | [] | The root paths, by default it is an empty array which basically disables static content |
| static-content.serverCacheMaxFileSize | 0 | |
| static-content.serverCacheTimeoutSeconds | 0 | |
| static-content.browserCacheTimeoutSeconds | 0 | |
| static-content.type | file | file or jar |

### Config supplied in JAR

````
wookiee-socko {
  manager = "com.webtrends.harness.component.socko.SockoManager"
  enabled = true
  dynamic-component = true

  server-name=Webtrends Harness
  hostname=localhost
  port=9091

  num-handler-routees=5

  static-content {
    rootPaths = []
    serverCacheMaxFileSize = 0
    serverCacheTimeoutSeconds = 0
    browserCacheTimeoutSeconds = 0
    type = "file"
  }
}
akka.actor.deployment {
  /system/component/wookiee-socko/Socko/socko-base {
    router = round-robin
    nr-of-instances = 3
  }
  /system/component/wookiee-socko/static-content-handler {
    router = round-robin
    nr-of-instance = 3
  }
}

```