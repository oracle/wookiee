wookiee-web
===========

Wookiee Web is a Component that provides a Helidon-based HTTP/WS server for Wookiee. It is a wrapper around the Helidon WebServer class, and provides a simple way to create a Helidon-based HTTP/WS server for your Wookiee Service.
It should be considered the most modern and preferred way to create a Wookiee Service that provides HTTP/WS endpoints.

# Usage
## Configuration
To use Wookiee Web, you must first add it as a dependency to your project.
```xml
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-web_2.13</artifactId>
    <version>${wookiee.version}</version>
</dependency>
```

Then you must add the following configuration (which has mostly the below defaults) to your `application.conf` file on the hosting server. See [reference.conf](src/main/resources/reference.conf) for more details.
```hocon
wookiee-web {
  # What port we host EndpointType.INTERNAL endpoints
  internal-port = 8080
  # What port we host EndpointType.EXTERNAL endpoints
  external-port = 8081
  
  # How long we wait for a request to complete
  internal-request-timeout = 300 seconds
  external-request-timeout = 60 seconds
    
  secure {
    # Set these to enable support of WSS and HTTPS
    # keystore-path = "keystore.jks"
    # keystore-passphrase = "password"
  }
    
  cors {
    # This corresponds to the origins we'll allow through via CORS
    # Anything not in this list will return 403
    # If empty, will allow all origins
    internal-allowed-origins = ["http://origin.safe"]
    # Corresponding allowed Origins list applied only to external endpoints
    external-allowed-origins = []
  }

  # Override these values if you want the websocket to be kept alive.
  # interval will decide the frequency of ping message that is used to keep the websocket alive.
  websocket-keep-alives {
    enabled = false
    interval = 30s
  }
}
```

## HTTP Endpoints
### Object-Oriented Registration
To register an HTTP endpoint, you must first create a class that extends the [HttpCommand](src/main/scala/com/oracle/infy/wookiee/component/web/http/HttpCommand.scala) class.
This class requires you to implement the `method`, `path`, `endpointType`, and `execute` methods to define the HTTP method, path, whether it should be available Internally or Externally (or both), and behavior of the endpoint, respectively.
See an example at [ExternalHttpCommand](../examples/advanced-communication/src/main/scala/com/oracle/infy/wookiee/communication/command/ExternalHttpCommand.scala).

```scala
import com.oracle.infy.wookiee.component.web.http.HttpCommand
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects._

class ExternalHttpCommand(implicit config: Config, ec: ExecutionContext) extends HttpCommand {
  // Required: Can be any valid HTTP method
  override def method: String = "GET"

  // Required: Corresponds to the path /external/* where '*' can be anything and will be passed along to the execution
  override def path: String = "/external/$somevalue"

  // Required: Whether this endpoint should be available internally, externally, or both
  override def endpointType: EndpointType = EndpointType.EXTERNAL

  // Required: Main business logic for HTTP calls
  override def execute(input: WookieeRequest): Future[WookieeResponse] = {
    val pathSegment = input.pathSegments("somevalue")
    val queryParam = input.queryParameters.getOrElse("query", "default")
    Future.successful(
      WookieeResponse(
        Content(s"[$pathSegment,$queryParam,${input.content.asString}]"), // required
        StatusCode(201), // defaults to 200
        Headers(Map("X-Test-Header" -> "wookiee-web")), // defaults to empty
        "text/plain" // defaults to application/json
      )
    )
  }

  // Optional: Can override this to provide custom error handling if any uncaught exceptions occur
  override def errorHandler(ex: Throwable): WookieeResponse = super.errorHandler(ex)

  // Optional: Can override this to set default response headers and allowed headers and origins for CORS
  override def endpointOptions: EndpointOptions = super.endpointOptions
}

```

Then, you must simply register the class with the [WookieeEndpoints](src/main/scala/com/oracle/infy/wookiee/component/web/WookieeEndpoints.scala) object.

```scala


WookieeEndpoints.registerEndpoint(new ExternalHttpCommand)
```

This can be done very easily during startup of your Service if you have your main Wookiee Service class extend the [WookieeHttpService](src/main/scala/com/oracle/infy/wookiee/component/web/WookieeHttpService.scala) trait.

```scala
class MyService(config: Config) extends WookieeHttpService(config) {
  override val name: String = "My Wookiee Service"

  override def addCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    WookieeEndpoints.registerEndpoint(new ExternalHttpCommand)
  }
}

```

### Functional Registration
To register an HTTP endpoint functionally, you must first create a function that takes a [`WookieeRequest`](src/main/scala/com/oracle/infy/wookiee/component/web/http/HttpObjects.scala) and returns a [`WookieeResponse`](src/main/scala/com/oracle/infy/wookiee/component/web/http/HttpObjects.scala).
The functional endpoint can be accessed in [WookieeEndpoints](src/main/scala/com/oracle/infy/wookiee/component/web/WookieeEndpoints.scala) via the `registerEndpoint` method.
Note that this example has the same behavior as the Object-Oriented example above.
```scala


// These two types can by anything
case class InputHolder(somevalue: String)

case class OutputHolder(somevalue: String)

WookieeEndpoints.registerEndpoint(
  "simple-get-command",
  "/functional/$somevalue",
  "GET",
  EndpointType.EXTERNAL,
  (input: WookieeRequest) =>
    Future {
      val pathSegment = input.pathSegments("somevalue")
      val queryParam = input.queryParameters.getOrElse("query", "default")
      InputHolder(s"$pathSegment,$queryParam,${input.content.asString}")
    },
  (input: InputHolder) =>
    Future {
      OutputHolder(input.somevalue)
    },
  (output: OutputHolder) =>
    WookieeResponse(
      Content(output.somevalue), // required
      StatusCode(201), // defaults to 200
      Headers(Map("X-Test-Header" -> "wookiee-web")), // defaults to empty
      "text/plain" // defaults to application/json
    ),
  // Optional: Error handler for uncaught exceptions
  (ex: Throwable) => WookieeResponse(Content(ex.getMessage), StatusCode(500)),
  // Optional: Endpoint options for default headers and CORS allowed headers
  EndpointOptions.default
)
```

### Localization
The WookieeRequest object that comes along with each HTTP request has a `locale` field that can be used to determine the locale of the request.
This field is populated by the `Accept-Language` header of the request, and can be used to determine the locale of the request.
```scala
override def execute(input: WookieeRequest): Future[WookieeResponse] = {
  val locales: List[Locale] = input.locale
  // Do something with the locale info
}
```

## Websocket Endpoints
### Object-Oriented Registration
To register a Websocket endpoint, you must first create a class that extends the [WookieeWebsocket](src/main/scala/com/oracle/infy/wookiee/component/web/ws/WookieeWebsocket.scala) class.
This class requires you to implement the `path`, `endpointType`, and `handleText` methods to define the path, whether it should be available Internally or Externally (or both), and behavior for each message, respectively.
See an example at [ExternalWSHandler](../examples/advanced-communication/src/main/scala/com/oracle/infy/wookiee/communication/ws/ExternalWSHandler.scala).

```scala

import javax.websocket.Session

case class AuthHolder(username: String, password: String)

class ExternalWSHandler(implicit conf: Config, ec: ExecutionContext) extends WookieeWebsocket[AuthHolder] {
  // Required: WS path to host this endpoint, segments starting with '$' will be treated as wildcards
  override def path: String = "/external/$somevalue"

  // Required: Whether this endpoint should be available internally, externally, or both
  override def endpointType: EndpointType = EndpointType.BOTH

  // Required: Behavior for each message received
  override def handleText(text: String, request: WookieeRequest, authInfo: Option[AuthHolder])(
    implicit session: Session
  ): Unit = {
    val pathSegment = request.pathSegments("somevalue")
    val queryParam = request.queryParameters.getOrElse("query", "default")

    if (text == "close") {
      close() // Close the websocket session
    } else {
      reply(s"Got message: [$text,$pathSegment,$queryParam]")
    }
  }

  // Optional: Called once on each new connection after handshake, throw an error to automatically close the connection
  override def handleAuth(request: WookieeRequest): Future[Option[AuthHolder]] = Future {
    Some(
      AuthHolder(
        request.queryParameters.getOrElse("username", "default"),
        request.queryParameters.getOrElse("password", "default")
      )
    )
  }

  // Optional: Called when any error occurs during handleText
  override def handleError(request: WookieeRequest, authInfo: Option[Auth])(
    implicit session: Session
  ): Throwable => Unit = { throwable: Throwable =>
    close(Some(("Encountered an error, closing socket", 1008)))
  }

  // Optional: Will be automatically called after the session is closed for any reason
  override def onClosing(auth: Option[Auth]): Unit = ()

  // Optional: Can override this to set default response headers and allowed headers and origins for CORS
  override def endpointOptions: EndpointOptions = EndpointOptions.default.copy(allowedOrigins = Some(AllowSome(List("http://origin.safe"))))
}
```

Then, you must simply register the class with the [WookieeEndpoints](src/main/scala/com/oracle/infy/wookiee/component/web/WookieeEndpoints.scala)
object.

```scala


WookieeEndpoints.registerWebsocket(new ExternalWSHandler)
```

### Functional Registration
To register a Websocket endpoint functionally, you must create a series of functions that handle authentication, messages, and error conditions.
The functional endpoint can be accessed in [WookieeEndpoints](src/main/scala/com/oracle/infy/wookiee/component/web/WookieeEndpoints.scala) via the `registerWebsocket` method.
Note that this example has the same behavior as the Object-Oriented example above.

```scala


case class AuthHolder(username: String, password: String)

WookieeEndpoints.registerWebsocket(
  path = "/ws/$userId/functional",
  endpointType = EndpointType.BOTH,
  handleInMessage = (text: String, interface: WebsocketInterface, authInfo: Option[AuthHolder]) => {
    val pathSegment = input.pathSegments("somevalue")
    val queryParam = input.queryParameters.getOrElse("query", "default")

    if (text == "close") {
      interface.close() // Close the websocket session
    } else {
      interface.reply(s"Got message: [$text,$pathSegment,$queryParam]")
    }
  },
  authHandler = (request: WookieeRequest) =>
    Future {
      Some(
        AuthHolder(
          request.queryParameters.getOrElse("username", "default"),
          request.queryParameters.getOrElse("password", "default")
        )
      )
    },
  onCloseHandler = (auth: Option[AuthHolder]) => (),
  // Can access the original WookieeRequest object via `interface.request`
  wsErrorHandler = (interface: WebsocketInterface, message: String, _: Option[AuthHolder]) => {
    _: Throwable =>
      log.info(s"Encountered an error on session [${interface.request}] and message [$message], closing socket")
      interface.close(Some(("Encountered an error, closing socket", 1008)))
  },
  endpointOptions = EndpointOptions.default
)
```