# Wookiee - Component: gRPC

Entry name for config is "wookiee-grpc-component". This would be the string you would use in lib-components if using that mechanism to load components.

### Example Configuration
```
wookiee-system {
  ## MUST HAVE EITHER THIS..
  zookeeper-config {
    connect-string = "localhost:2121"
  }
  ## ..OR THIS
  wookiee-zookeeper {
    quorum = "localhost:2121"
  }

  ## ALL REQUIRED
  wookiee-grpc-component {
    grpc {
      port = 8089 # port on which we host gRPC server
      zk-discovery-path = "/grpc/local_dev" # the client will use this path to discover this gRPC service
      server-host-name = "localhost" # fqdn of this server
    }
  }
}
```

### Config supplied in JAR
[reference.conf](src/main/resources/reference.conf)