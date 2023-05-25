package com.oracle.infy.wookiee.component.helidon.web.http

import java.nio.charset.Charset

object HttpObjects {

  // For determining which port(s) to host an endpoint on
  object EndpointType extends Enumeration {
    type EndpointType = Value
    val INTERNAL, EXTERNAL, BOTH = Value
  }

  // These are all of the more optional bits of configuring an endpoint
  case class EndpointOptions(
      defaultHeaders: Headers = Headers(Map()),
      corsSettings: Option[CorsAllowed] = None,
      routeTimerLabel: Option[String] = None,
      requestHandlerTimerLabel: Option[String] = None,
      businessLogicTimerLabel: Option[String] = None,
      responseHandlerTimerLabel: Option[String] = None
  )

  object EndpointOptions {
    val default: EndpointOptions = EndpointOptions()
  }

  case class CorsAllowed(methods: List[String] = List("*"), origins: List[String] = List("*"))

  object Content {
    def apply(content: String): Content = Content(content.getBytes(Charset.forName("UTF-8")))
  }

  // Request/Response body content
  case class Content(value: Array[Byte]) {
    def asString: String = new String(value, Charset.forName("UTF-8"))
  }
  // Request/Response headers
  case class Headers(mappings: Map[String, List[String]] = Map())
  // Response status code
  case class StatusCode(code: Int = 200)

  // Object holding all of the request information
  case class WookieeRequest(
      content: Content,
      pathSegments: Map[String, String],
      queryParameters: Map[String, List[String]],
      headers: Headers
  )

  // Object holding all of the response information
  case class WookieeResponse(content: Content, statusCode: StatusCode = StatusCode(), headers: Headers = Headers())
}
