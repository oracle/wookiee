# Wookiee - Component: Netty (HTTP)

For Configuration information see [Netty Config](docs/config.md)

The Netty component provides both server http and websocket functionality using Netty.

For a working example see [Wookiee - Netty Example](../../examples/example-netty)

## Config
```json
wookiee-netty {
  manager = "com.webtrends.harness.component.netty.NettyManager"
  enabled = true

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

The configuration is fairly straight forward and you shouldn't need to modify it too much.

## Netty Server
The server provides base http and websocket functionality.  The user can customize this functionality by adding their own http handlers with websocket frame processing.

### Adding an http handler
The primary function of having a http server is to allow requests to be made to your service and a subsequent response after some business logic is executed. For more information on building the handlers, see [io.netty Documentation](http://io.netty/wiki). To add a handler, you can do the following:
```Scala
class MyHandler extends BaseInboundHandler {

  override def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest) : Boolean =  {
    var msgHandled = true
    
    (req.getMethod, req.getUri) match {
      case (GET, "/foo") =>
        val t = "".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString)
        sendContent(ctx, req, s""" {"message": "myresponse", "time": "$t"} """, "application/json")
      case _ => msgHandled = false
    }
    
    msgHandled
  }
}
```

### Adding TextWebSocketFrame processing
Providing custom websocket frame processing to your service can be done by overriding the WebSocketFrame function of the WebSocket trait and then adding it as a trait to your handler.
```Scala
trait MyWebSocket extends WebSocket {

  override def WebSocketFrame(ctx: ChannelHandlerContext, frame: TextWebSocketFrame): Unit = {
    val request = frame.text()

    log.info(s"request: $request")
    ctx.writeAndFlush(new TextWebSocketFrame("Echo from example server: ".concat(request.toString)))
  }
}
...
new MyHandler with MyWebSocket
...
```


