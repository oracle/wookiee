package com.oracle.infy.wookiee.component.web.http

import com.oracle.infy.wookiee.model.CaseInsensitiveMap
import org.json4s.{JString, JValue}
import org.json4s.jackson.JsonMethods.parseOpt

import java.nio.charset.Charset
import java.util
import java.util.Locale
import java.util.Locale.LanguageRange
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

object HttpObjects {

  // For determining which port(s) to host an endpoint on
  object EndpointType extends Enumeration {
    type EndpointType = Value
    val INTERNAL, EXTERNAL, BOTH = Value
  }

  // These are all of the more optional bits of configuring an endpoint
  object EndpointOptions {
    def default: EndpointOptions = EndpointOptions(Headers(), None, None, None, None, None)

    def apply(): EndpointOptions = default

    def apply(
        defaultHeaders: Headers, // Will show up on all responses
        allowedHeaders: Option[CorsWhiteList] // CORS will report these as available headers, or all if empty
    ): EndpointOptions =
      EndpointOptions(defaultHeaders, allowedHeaders, None, None, None, None)
  }

  case class EndpointOptions(
      defaultHeaders: Headers = Headers(), // Will show up on all responses
      allowedHeaders: Option[CorsWhiteList] = None, // CORS will report these as available headers, or all if empty
      routeTimerLabel: Option[String], // Functional and Object Oriented: Time of entire request
      requestHandlerTimerLabel: Option[String], // Functional only: Time of requestHandler
      businessLogicTimerLabel: Option[String], // Functional only: Time of businessLogic
      responseHandlerTimerLabel: Option[String] // Functional only: Time of responseHandler
  )

  // The headers that are allowed for a particular endpoint
  // If not specified, all headers are allowed
  // Note:
  //   * Allowed methods is returned dynamically based on what endpoints are registered
  //   * Allowed origins is set at the global config level under wookiee-web.cors.allowed-origins = []
  object CorsWhiteList {
    def allowAll: CorsWhiteList = CorsWhiteList()
    def apply(): CorsWhiteList = AllowAll()
    def apply(toCheck: java.util.Collection[String]): CorsWhiteList = AllowSome(toCheck.asScala.toList)
    def withList(toCheck: java.util.Collection[String]): CorsWhiteList = CorsWhiteList(toCheck)
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
    def empty: Content = Content()
    def apply(): Content = Content(Array.empty[Byte])
    def apply(content: String): Content = Content(content.getBytes(Charset.forName("UTF-8")))
  }

  case class Content(value: Array[Byte]) {
    def asString: String = new String(value, Charset.forName("UTF-8"))
  }

  object Headers {
    def default: Headers = Headers()

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

    def withMappings(mappings: java.util.Map[String, java.util.Collection[String]]): Headers =
      Headers(mappings)
  }

  // Request/Response headers
  // Keys are case insensitive
  case class Headers(private val mappings: Map[String, List[String]]) {

    val caseInsensitiveMap: AtomicReference[CaseInsensitiveMap[List[String]]] =
      new AtomicReference(CaseInsensitiveMap(mappings))

    // Returned map has case insensitive keys (and updates will preserve this, just don't do mappings)
    def getMap: Map[String, List[String]] = caseInsensitiveMap.get()

    // Returned map has case insensitive keys (and updates will preserve this)
    def getJavaMap: java.util.Map[String, java.util.List[String]] = {
      val ciMap: util.TreeMap[String, java.util.List[String]] =
        new util.TreeMap[String, java.util.List[String]](
          String.CASE_INSENSITIVE_ORDER
        )
      ciMap.putAll(
        caseInsensitiveMap
          .get
          .map {
            case (key, value) =>
              key -> value.asJava
          }
          .asJava
      )
      ciMap
    }

    // Will return 'null' if the key doesn't exist
    def getJavaValue(key: String): java.util.List[String] =
      caseInsensitiveMap.get.get(key).map(_.asJava).getOrElse(new util.ArrayList[String]())
    // Will return None if key doesn't exist
    def maybeStringValue(key: String): Option[String] = caseInsensitiveMap.get.get(key).map(_.mkString(","))
    // Will return "" if the key doesn't exist or value is empty
    def getStringValue(key: String): String = caseInsensitiveMap.get.get(key).map(_.mkString(",")).getOrElse("")
    // Will return None if key doesn't exist
    def maybeValue(key: String): Option[List[String]] = caseInsensitiveMap.get.get(key)
    // Will return empty list if key doesn't exist or value is empty
    def getValue(key: String): List[String] = caseInsensitiveMap.get.get(key).getOrElse(List())

    def putValue(key: String, value: List[String]): Unit = {
      caseInsensitiveMap.updateAndGet(_.updated(key, value))
      ()
    }

    def putValue(key: String, value: java.util.List[String]): Unit = {
      caseInsensitiveMap.updateAndGet(_.updated(key, value.asScala.toList))
      ()
    }

    def foreach(f: ((String, List[String])) => Unit): Unit = caseInsensitiveMap.get().foreach(f)

    override def toString: String = caseInsensitiveMap.toString
  }

  object StatusCode {
    def ok: StatusCode = StatusCode()
    def apply(): StatusCode = StatusCode(200)
  }

  // Response status code
  case class StatusCode(code: Int)

  object WookieeRequest {
    // Will return an empty request object, mainly useful for testing
    def empty: WookieeRequest = WookieeRequest()
    def apply(): WookieeRequest = new WookieeRequest(Content(""), Map(), Map(), Headers())

    def apply(content: String): WookieeRequest =
      WookieeRequest(Content(content))

    def apply(
        content: Content,
        pathSegments: Map[String, String] = Map(),
        queryParameters: Map[String, String] = Map(),
        headers: Headers = Headers()
    ): WookieeRequest =
      new WookieeRequest(content, CaseInsensitiveMap(pathSegments), CaseInsensitiveMap(queryParameters), headers)

    def apply(
        content: Content,
        pathSegments: java.util.Map[String, String],
        queryParameters: java.util.Map[String, String],
        headers: Headers
    ): WookieeRequest =
      WookieeRequest(content, pathSegments.asScala.toMap, queryParameters.asScala.toMap, headers)

    def build(
        content: Content,
        pathSegments: java.util.Map[String, String],
        queryParameters: java.util.Map[String, String],
        headers: Headers
    ): WookieeRequest =
      WookieeRequest(content, pathSegments, queryParameters, headers)
  }

  // Object holding all of the request information
  // Not directly constructable (to ensure maps are case-insensitive ones), go through the 'apply' methods above
  // Note that this object is also a mutable Map and can store any additional information
  case class WookieeRequest private (
      content: Content, // Actual content bytes of the request
      pathSegments: Map[String, String], // Case insensitive map
      queryParameters: Map[String, String], // Case insensitive map
      headers: Headers // Keys are case insensitive
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

    // Below getters are all case insensitive
    def queryParameter(key: String): String =
      queryParameters(key)

    def getQueryParameter(key: String): Option[String] =
      queryParameters.get(key)

    // Should correspond to the path segments in the route
    def pathSegment(key: String): String =
      pathSegments(key)

    def getPathSegment(key: String): Option[String] =
      pathSegments.get(key)

    def header(key: String): List[String] =
      headers.getValue(key)

    def getHeader(key: String): Option[List[String]] =
      headers.maybeValue(key)

    // Will return "" if the key doesn't exist or value is empty
    // Returns a comma-seperated list of values (as does getHeaderValue)
    def headerValue(key: String): String =
      headers.getStringValue(key)

    def getHeaderValue(key: String): Option[String] =
      headers.maybeStringValue(key)

    private val createdTime: Long = System.currentTimeMillis()
    def getCreatedTime: Long = createdTime

    def contentString(): String = content.asString

    def locales: List[Locale] = {
      val localeString = headers.getStringValue("accept-language")
      if (localeString.nonEmpty)
        Try {
          LanguageRange
            .parse(localeString)
            .asScala
            .map(language => Locale.forLanguageTag(language.getRange))
            .toList
        }.getOrElse(Nil)
      else Nil
    }

    // The query string and path, will look like '/path?query=string'
    def getQuery: String =
      this.get(queryKey).map(_.toString).getOrElse("")

    override def toString(): String = {
      s"""Request:
         |Content = [${Try(content.asString).getOrElse("Could not parse content as string")}]
         |Headers = [${headers.getMap.mkString(", ")}]
         |Query Parameters = [${queryParameters.mkString(", ")}]
         |Path Segments = [${pathSegments.mkString(", ")}]
         |Additional Parameters = [${this.mkString(", ")}]
         |""".stripMargin
    }

    private val queryKey = "wookiee-query"

    private[wookiee] def appendQuery(query: String): Unit =
      this.update(queryKey, query)
  }

  object WookieeResponse {
    def empty: WookieeResponse = WookieeResponse()
    def apply(): WookieeResponse = WookieeResponse(Content(""), StatusCode(), Headers(), "application/json")

    def apply(statusCode: StatusCode): WookieeResponse =
      WookieeResponse(Content(""), statusCode, Headers(), "application/json")

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
    def contentJson(): JValue = parseOpt(contentString()).getOrElse(JString(contentString()))
  }
}
