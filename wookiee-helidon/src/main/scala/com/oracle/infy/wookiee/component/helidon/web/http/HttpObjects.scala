package com.oracle.infy.wookiee.component.helidon.web.http

import java.nio.charset.Charset

object HttpObjects {

  // For determining which port(s) to host an endpoint on
  object EndpointType extends Enumeration {
    type EndpointType = Value
    val INTERNAL, EXTERNAL, BOTH = Value
  }

  // These are all of the more optional bits of configuring an endpoint
  object EndpointOptions {
    val default: EndpointOptions = EndpointOptions()
  }

  case class EndpointOptions(
      defaultHeaders: Headers = Headers(Map()),
      allowedHeaders: Option[CorsWhiteList] = None,
      routeTimerLabel: Option[String] = None,
      requestHandlerTimerLabel: Option[String] = None,
      businessLogicTimerLabel: Option[String] = None,
      responseHandlerTimerLabel: Option[String] = None
  )

  // The headers that are allowed for a particular endpoint
  // If not specified, all headers are allowed
  // Note:
  //   * Allowed methods is returned dynamically based on what endpoints are registered
  //   * Allowed origins is set at the global config level under wookiee-helidon.web.cors.allowed-origins = []
  object CorsWhiteList {
    def apply(): CorsWhiteList = AllowAll()
    def apply(toCheck: List[String]): CorsWhiteList = AllowSome(toCheck)
  }

  // Trait for the allowed origins hosts
  trait CorsWhiteList {
    def allowed(toCheck: List[String]): List[String]
    def allowed(toCheck: String): List[String] = allowed(List(toCheck))
  }

  // Let in anything, will always return the true for any hosts list
  case class AllowAll() extends CorsWhiteList {
    override def allowed(toCheck: List[String]): List[String] = toCheck
  }

  // Only let in the hosts specified in the white list
  case class AllowSome(whiteList: List[String]) extends CorsWhiteList {
    override def allowed(toCheck: List[String]): List[String] = toCheck.intersect(whiteList)
  }

  // Request/Response body content
  object Content {
    def apply(content: String): Content = Content(content.getBytes(Charset.forName("UTF-8")))
  }

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
      queryParameters: Map[String, String],
      headers: Headers
  ) {
    private val createdTime: Long = System.currentTimeMillis()
    def getCreatedTime: Long = createdTime
  }

  // Object holding all of the response information
  case class WookieeResponse(
      content: Content,
      statusCode: StatusCode = StatusCode(),
      headers: Headers = Headers(),
      contentType: String = "application/json"
  )
}
