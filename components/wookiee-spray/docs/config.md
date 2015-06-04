# Wookiee - Component: Spray Config

Entry name for config is "wookiee-spray". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.spray.SprayManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| http-port | 8080 | |

See Spray config information for more specific information around Spray

* [Spray Can](http://spray.io/documentation/1.2.2/spray-can/configuration/)
* [Spray Routing](http://spray.io/documentation/1.2.2/spray-routing/configuration/)

### Config supplied in JAR

````
wookiee-spray {
  manager = "com.webtrends.harness.component.spray.SprayManager"
  enabled = true
  dynamic-component = true

  # The port to run the http server on
  http-port = 9090
}
spray {
  can {
    server {
      server-header = "harness"
      request-timeout = 60s
      idle-timeout = 120s

      # Enables/disables the addition of a `Remote-Address` header
      # holding the clients (remote) IP address.
      remote-address-header = on
      # Enables/disables support for statistics collection and querying.
      stats-support = on
    }
    parsing {
      max-uri-length: 16k
    }
  }
  client {
    idle-timeout = 120 s
    request-timeout = 60 s
  }
  host-connector {
    max-connections = 10
    max-retries = 2
    pipelining = on
  }
}
akka.actor.deployment {
  /system/component/wookiee-spray/spray-server/spray-base {
    router = round-robin
    nr-of-instances = 3
  }
}

```