package com.oracle.infy.wookiee.component.web.http

import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import java.nio.charset.Charset
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

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
      defaultHeaders: Headers = Headers(), // Will show up on all responses
      allowedHeaders: Option[CorsWhiteList] = None, // CORS will report these as available headers, or all if empty
      routeTimerLabel: Option[String] = None, // Functional and Object Oriented: Time of entire request
      requestHandlerTimerLabel: Option[String] = None, // Functional only: Time of requestHandler
      businessLogicTimerLabel: Option[String] = None, // Functional only: Time of businessLogic
      responseHandlerTimerLabel: Option[String] = None // Functional only: Time of responseHandler
  )

  // The headers that are allowed for a particular endpoint
  // If not specified, all headers are allowed
  // Note:
  //   * Allowed methods is returned dynamically based on what endpoints are registered
  //   * Allowed origins is set at the global config level under wookiee-web.cors.allowed-origins = []
  object CorsWhiteList {
    def apply(): CorsWhiteList = AllowAll()
    def apply(toCheck: java.util.Collection[String]): CorsWhiteList = AllowSome(toCheck.asScala.toList)
    def apply(toCheck: List[String]): CorsWhiteList = AllowSome(toCheck)
  }

  // Trait for the allowed origins hosts
  trait CorsWhiteList {
    def allowed(toCheck: List[String]): List[String]
    def allowed(toCheck: java.util.Collection[String]): List[String] = allowed(toCheck.asScala.toList)
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

  object Headers {
    def apply(): Headers = Headers(Map.empty[String, List[String]])

    // For java interop
    def apply(mappings: java.util.Map[String, java.util.Collection[String]]): Headers =
      Headers(
        mappings
          .asScala
          .map {
            case (key, value) =>
              key -> value.asScala.toList
          }
          .toMap
      )
  }

  // Request/Response headers
  // Keys are case insensitive
  case class Headers(private val mappings: Map[String, List[String]]) {

    val caseInsensitiveMap: util.TreeMap[String, List[String]] = new util.TreeMap[String, List[String]](
      String.CASE_INSENSITIVE_ORDER
    )
    caseInsensitiveMap.putAll(mappings.asJava)

    def getMap: Map[String, List[String]] = mappings

    def getJavaMap: java.util.Map[String, java.util.List[String]] =
      mappings.map {
        case (key, value) =>
          key -> value.asJava
      }.asJava

    // Will return 'null' if the key doesn't exist
    def getJavaValue(key: String): java.util.List[String] =
      Option(caseInsensitiveMap.get(key)).map(_.asJava).getOrElse(new util.ArrayList[String]())
    // Will return None if key doesn't exist
    def maybeStringValue(key: String): Option[String] = Option(caseInsensitiveMap.get(key)).map(_.mkString(","))
    // Will return "" if the key doesn't exist or value is empty
    def getStringValue(key: String): String = Option(caseInsensitiveMap.get(key)).map(_.mkString(",")).getOrElse("")
    // Will return None if key doesn't exist
    def maybeValue(key: String): Option[List[String]] = Option(caseInsensitiveMap.get(key))
    // Will return empty list if key doesn't exist or value is empty
    def getValue(key: String): List[String] = Option(caseInsensitiveMap.get(key)).getOrElse(List())

    def putValue(key: String, value: List[String]): Unit = {
      caseInsensitiveMap.put(key, value)
      ()
    }

    def putValue(key: String, value: java.util.List[String]): Unit = {
      caseInsensitiveMap.put(key, value.asScala.toList)
      ()
    }
  }

  object StatusCode {
    def apply(): StatusCode = StatusCode(200)
  }

  // Response status code
  case class StatusCode(code: Int)

  object WookieeRequest {
    // Will return an empty request object, mainly useful for testing
    def apply(): WookieeRequest = WookieeRequest(Content(""), Map(), Map(), Headers())
  }

  // Object holding all of the request information
  // Note that this object is also a mutable Map and can store any additional information
  case class WookieeRequest(
      content: Content,
      pathSegments: Map[String, String],
      queryParameters: Map[String, String],
      headers: Headers
  ) extends mutable.LinkedHashMap[String, Any] {

    // Add a map of parameters to the request
    def appendMap(params: Map[String, Any]): Unit = params foreach {
      this += _
    }

    // Add a single parameter to the request
    def addValue(key: String, value: Any): WookieeRequest = this += key -> value

    // Get a single parameter from the request
    // Will return None if we couldn't cast the value to the type specified
    def getValue[T](key: String)(implicit tag: ClassTag[T]): Option[T] = {
      get(key) match {
        case Some(v) =>
          v match {
            case x: T => Some(x)
            case _    => None
          }
        case None => None
      }
    }

    private val createdTime: Long = System.currentTimeMillis()
    def getCreatedTime: Long = createdTime

    def contentString(): String = content.asString
  }

  object WookieeResponse {
    def apply(): WookieeResponse = WookieeResponse(Content(""), StatusCode(), Headers(), "application/json")

    def apply(content: Content): WookieeResponse =
      WookieeResponse(content, StatusCode(), Headers(), "application/json")

    def apply(content: Content, statusCode: StatusCode): WookieeResponse =
      WookieeResponse(content, statusCode, Headers(), "application/json")

    def apply(content: Content, statusCode: StatusCode, headers: Headers): WookieeResponse =
      WookieeResponse(content, statusCode, headers, "application/json")

    def apply(content: Content, headers: Headers): WookieeResponse =
      WookieeResponse(content, StatusCode(), headers, "application/json")
  }

  // Object holding all of the response information
  case class WookieeResponse(
      content: Content,
      statusCode: StatusCode,
      headers: Headers,
      // If specified in `headers`, that value will take precedence
      contentType: String
  ) {
    def code(): Int = statusCode.code
    def contentString(): String = content.asString
    def contentJson(): JValue = parse(contentString())
  }
}
