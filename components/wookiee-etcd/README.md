# Wookiee - Component: Etcd

For Configuration information see [Etcd Config](docs/config.md)

The etcd component allows users to use Wookiee in a micro-service configuration which can be configured to publish and
lookup address in an Etcd service provided by docker or an other service such as CoreOS

### Discoverable

This is the most important trait and will have to have a few functions to be overridden when added to a Service.   Then override the function:
```
    def identity() : String {} // by default this returns the service name
    def announcement() : String {} // this needs to be overridden to register the data needed in etcd
```
This method will actually handle the publishing and removal of the service from etcd.  

### Configuration

# Base
Base configuration is simple, should not need to be changed in most cases.
```json
wookiee-etcd {
    etcd-endpoint = "http://localhost:4001"
}
```