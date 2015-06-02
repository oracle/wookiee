# Wookiee - Component: Netty Config

Entry name for config is "wookiee-netty". This would be the string you would use in lib-components if using that mechanism to load components.

| Name | Default | Description |
|:-----|:--------|:------------|
| manager | com.webtrends.harness.component.netty.NettyManager | This should never be overridden or changed, and changing this would most likely cause it to not start up. |
| enabled | true | whether this component is enabled or not. |
| dynamic-component | true | enables loading up the component dynamically |
| port | 9091 | port that netty listens on |
| websocket-path | /stream | the location that any websocket requests would be sent to |
| server-header | harness | host name set in HttpHeader |
| request-timeout | 60s | The time allowed to respond to a request |
| idle-timeout | 120s | The time after which an idle connection will be closed |
| static-files.filesource | file | file or jar, so whether it is being pulled from the disk or as a resource from a jar |
| static-files.location | "" | if not set then will be disabled, if set it is simply the root location to look for files |
| tcp.tcp-nodelay | off | Enables the TCP_NODELAY flag, i.e. disables Nagle.s algorithm |
| tcp.tcp-keepalive | off | Enables TCP Keepalive, subject to the O/S kernel.s configuration |
| tcp.send-buffer-size | 0b | Sets the send buffer size of the Sockets |
| tcp.receive-buffer-size | 0b | Sets the receive buffer size of the Sockets |

### Config supplied in JAR

````
wookiee-netty {
  manager = "com.webtrends.harness.component.netty.NettyManager"
  enabled = true
  dynamic-component = true

  # The port on which to run the server
  port = 9091
  # The URI to create a websocket connection
  websocket-path = "/stream"
  # The host name to set in HttpHeader
  server-header = "harness"
  # The time allowed to respond to a request
  request-timeout = 60s
  # The time after which an idle connection will be closed
  idle-timeout = 120s

  static-files {
    # Whether static files are pulled from JAR or File
    filesource = "file"
    location = ""
  }

  tcp {
    # Enables the TCP_NODELAY flag, i.e. disables Nagle.s algorithm
    tcp-nodelay = off
    # Enables TCP Keepalive, subject to the O/S kernel.s configuration
    tcp-keepalive = off
    # Sets the send buffer size of the Sockets,
    # set to 0b for platform default
    send-buffer-size = 0b
    # Sets the receive buffer size of the Sockets,
    # set to 0b for platform default
    receive-buffer-size = 0b
  }
}
akka.actor.deployment {
  /system/component/wookiee-netty/netty-server/netty-worker {
    router = round-robin
    nr-of-instances = 3
  }
}
```