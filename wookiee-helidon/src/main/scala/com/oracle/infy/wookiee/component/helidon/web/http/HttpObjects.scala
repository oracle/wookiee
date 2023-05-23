package com.oracle.infy.wookiee.component.helidon.web.http

import java.nio.charset.Charset

object HttpObjects {

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

  case class Content(value: Array[Byte]) {
    def asString: String = new String(value, Charset.forName("UTF-8"))
  }
  case class Headers(mappings: Map[String, List[String]] = Map())
  case class StatusCode(code: Int = 200)

  case class WookieeRequest(
      content: Content,
      pathSegments: Map[String, String],
      queryParameters: Map[String, List[String]],
      headers: Headers
  )
  case class WookieeResponse(content: Content, statusCode: StatusCode = StatusCode(), headers: Headers = Headers())
}
