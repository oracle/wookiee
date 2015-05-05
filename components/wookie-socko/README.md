# Wookie - Component: Socko (HTTP)

For Configuration information see [Socko Config](docs/config.md)

Socko is an Akka actor based library that wraps Netty. There is already a Netty component Wookie, however Socko takes away a bit of the work that is required in the Netty component to get your routes working correctly. The functionality and usage is very similar to that of Spray but instead of Spray under the hood you are using Netty. The big difference between Spray and Socko is that Socko supports websockets as well (not currently supported in the component). Currently however Spray is the only http based component that has a http client that you can use to make http requests.

For working example see [Wookie - Http Example](../../examples/example-http) or [Wookie - Rest Example](../../examples/example-rest)
## Config

```
wookie-socko {
  manager = "com.webtrends.harness.component.socko.SockoManager"
  enabled = true

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
  /system/component/wookie-socko/Socko/socko-base {
    router = round-robin
    nr-of-instances = 3
  }
  /system/component/wookie-socko/static-content-handler {
    router = round-robin
    nr-of-instance = 3
  }
}
```

The static-content section relates to giving the ability to serve static content to clients.

## Socko Server
The SockoManager which is the core component for the Socko component module, extends the SockoServer. SockoServer is started as a child actor for the root actor (for this component) SockoManager.

### Adding new routes to Socko
To manually add a new route to Socko is by adding handlers to the SockoRouteManager. The trait SockoService which can be used to extend your current service has a function called ```addhandler[T<:SockoRouteHandler](handlerName:String, handler:Class[T]) : Future[ActorRef]```. So you would create a new handler and then simply pass it into that function which would then add the handler. In essence this will send a message to the SockoManager which would then initialize the new handler as an actor child of itself, and then register it with the SockoRouteManager. You can see the code for this [here](src/main/scala/com/webtrends/harness/component/socko/SockoService.scala).

### Building a new Socko route handler

Building a handler is fairly straight forward and is helped with the use of the SockoRouteHandler abstract class. The handler should be separate from any commands or other actors that have specific actors, as the receive function for the handler actor requires a specific handling so that it can be part of the route registry. A simple handler would look like this:

```
class SockoPing extends SockoRouteHandler {
  import context.dispatcher
  override protected def handleSockoRoute(bean: SockoCommandBean): Future[Boolean] = {
    event match {
      case GET(Path("/ping")) =>
        val dtime = new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString()
        event.response.write("socko pong from httpexample: ".concat(dtime))
        Future { true }
      case _ => Future { false }
    }
  }
}
```

So a couple of things to note, firstly to handle the route you simply need to extend from SockoRouteHandler and then implement the handleSockoRoute function. At this point you can do anything you want with the incoming event, however in general you would have a variation of the above example where you are matching the event based on the method and the path. You also need to return a boolean future, which will basically state whether this handler actually managed the route or not. If it returns true it will stop processing any other routes, otherwise it will send the event on to the next route handler. If you want you can spin your handling off into another thread or future and return immediately, however that is up to the developer.

### Adding routes automagically

The true magic of the Socko component is that it can add routes automagically. This is explained in depth in the in the [SockoRoutes.md](docs/SockoRoutes.md) in more detail.