package com.oracle.infy.wookiee.component.web.client

import com.oracle.infy.wookiee.component.web.WebManager.WookieeWebException
import com.oracle.infy.wookiee.component.web.client.WookieeWebClient.ProxyConfig
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.util.HelidonUtil
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ThreadUtil
import io.helidon.common.http.{DataChunk, Http}
import io.helidon.common.reactive.Single
import io.helidon.webclient.Proxy.ProxyType
import io.helidon.webclient.{Proxy, WebClient, WebClientRequestBuilder, WebClientResponse}
import org.json4s.{DefaultFormats, Formats}

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

// Useful client for interacting with any endpoint, powered by Helidon
object WookieeWebClient {
  case class ProxyConfig(host: String, port: Int)

  // Will make a single call to a given endpoint, with the provided payload, headers, etc.
  // Not super performant since it has to build the client each time
  def oneOff(
      host: String,
      method: String,
      path: String,
      payload: String,
      headers: Map[String, String] = Map(),
      timeout: Duration = 15.seconds
  )(implicit ec: ExecutionContext = ExecutionContext.global): WookieeResponse = {
    // This client doesn't need to be shutdown
    val client = WebClient
      .builder()
      .baseUri(host)
      .build()

    Await.result(request(client, method, path, payload, headers, getQueryParams(path)), timeout)
  }

  def request(client: WebClient, method: String, path: String, payload: String)(
      implicit ec: ExecutionContext
  ): Future[WookieeResponse] =
    request(client, method, path, payload, Map(), Map())

  // General request method, should use this via WookieeWebClient or WookieeWebClient.oneOff
  def request(
      client: WebClient,
      method: String,
      path: String,
      payload: String,
      headers: Map[String, String],
      queryParams: Map[String, String]
  )(
      implicit ec: ExecutionContext
  ): Future[WookieeResponse] =
    requestAndRespond(client, method, path, payload.getBytes(StandardCharsets.UTF_8), headers, queryParams)

  def request(client: WebClient, method: String, path: String, payload: Array[Byte])(
      implicit ec: ExecutionContext
  ): Future[WookieeResponse] =
    requestAndRespond(client, method, path, payload, Map(), Map())

  // General request method, should use this via WookieeWebClient or WookieeWebClient.oneOff
  def requestAndExtract[T: Manifest](
      client: WebClient,
      method: String,
      path: String,
      payload: String
  )(
      implicit ec: ExecutionContext
  ): Future[T] =
    requestAndExtract[T](client, method, path, payload, Map.empty[String, String], Map.empty[String, String])

  // General request method, should use this via WookieeWebClient or WookieeWebClient.oneOff
  def requestAndExtract[T: Manifest](
      client: WebClient,
      method: String,
      path: String,
      payload: String,
      headers: Map[String, String],
      queryParams: Map[String, String]
  )(
      implicit ec: ExecutionContext
  ): Future[T] =
    requestAndRespond(client, method, path, payload.getBytes(StandardCharsets.UTF_8), headers, queryParams)
      .map(WookieeWebClient.extractObject[T](_))

  // General request method, should use this via WookieeWebClient or WookieeWebClient.oneOff
  def requestAndRespond(
      client: WebClient,
      method: String,
      path: String,
      payload: Array[Byte],
      headers: Map[String, String],
      queryParams: Map[String, String]
  )(implicit ec: ExecutionContext): Future[WookieeResponse] =
    try {
      val withHeaders =
        headers.foldLeft(methodRequested(client, method))((builder, header) => builder.addHeader(header._1, header._2))
      val withQueryParams =
        queryParams.foldLeft(withHeaders)(
          (builder, queryParam) => builder.queryParam(queryParam._1, queryParam._2)
        )

      val responseRef = new AtomicReference[WebClientResponse]()
      val data = DataChunk.create(payload)

      val future = HelidonUtil.completionToFuture(
        withQueryParams
          .path(path)
          .submit(Single.just(data)) // submit the payload here
          .thenAccept(response => {
            responseRef.set(response) // store the response
          })
      )

      future.map(_ => {
        val resp = responseRef.get() // return the stored response

        WookieeResponse(
          Content(resp.content().as(classOf[String]).await()),
          StatusCode(resp.status().code()),
          headerConversion(resp),
          resp.headers().value("Content-Type").orElse("application/json")
        )
      })
    } catch {
      case ex: Throwable =>
        // Wrap failures in a future
        Future.failed(ex)
    }

  // Useful method to get the actual content from a response
  def getContent(response: WookieeResponse): String =
    response.contentString()

  // Translates the method string to a Helidon WebClientRequestBuilder
  def methodRequested(client: WebClient, method: String): WebClientRequestBuilder = {
    method.toUpperCase match {
      case "GET" => client.get()
      // PATCH doesn't have a convenience method yet but this should work
      case "PATCH"   => client.method(Http.Method.PATCH)
      case "POST"    => client.post()
      case "PUT"     => client.put()
      case "DELETE"  => client.delete()
      case "HEAD"    => client.head()
      case "OPTIONS" => client.options()
      case "TRACE"   => client.trace()
      case _         => client.get()
    }
  }

  // Helper method to get query params from a URL
  def getQueryParams(query: String): Map[String, String] =
    query
      .drop(query.indexOf("?") + 1)
      .split("&")
      .flatMap { param =>
        param.split("=") match {
          case Array(key, value) => Some(key -> value)
          case _                 => None
        }
      }
      .toMap

  // Extract out a case class from a response
  // If response will not be code 200-299, set expectedStatus to the expected status code
  // @throws WookieeWebException if response code is not expectedStatus
  def extractObject[T: Manifest](resp: WookieeResponse, expectedStatus: Option[Int] = None)(
      implicit formats: Formats = DefaultFormats
  ): T = {
    resp match {
      // If expected status is empty, check for 200-299
      case resp: WookieeResponse
          if (expectedStatus.isEmpty && resp.code() >= 200 && resp.code() < 300) ||
            expectedStatus.contains(resp.code()) =>
        resp.contentJson().extract[T]
      case resp: WookieeResponse =>
        throw WookieeWebException(
          s"Unexpected status code: [${resp.code()}]. " +
            s"Expected: [${expectedStatus.map(_.toString).getOrElse("200-299")}]. " +
            s"Response: [${resp.contentString()}]",
          None,
          Some(resp.code())
        )
    }
  }

  private def headerConversion(response: WebClientResponse): Headers =
    Headers(
      response
        .headers()
        .toMap
        .asScala
        .map(header => header._1 -> header._2.asScala.toList)
        .toMap
    )
}

trait WebClientLike {

  def request(
      method: String,
      path: String,
      content: String = "",
      queryParams: Map[String, String] = Map(),
      headers: Map[String, String] = Map(),
      log: Boolean = true,
      contentType: String = "application/json"
  ): Future[WookieeResponse]
}

case class WookieeWebClient(baseUri: String, proxyConfig: Option[ProxyConfig] = None)
    extends WebClientLike
    with LoggingAdapter {
  implicit val ec: ExecutionContext = ThreadUtil.createEC("wookiee-webclient")

  protected[wookiee] val helidonClient: WebClient =
    (proxyConfig match {
      case Some(proxy) =>
        WebClient
          .builder()
          .proxy(
            Proxy.builder().`type`(ProxyType.HTTP).host(proxy.host).port(proxy.port).build()
          )
      case _ =>
        WebClient
          .builder()
    }).baseUri(baseUri)
      .build()

  def getClient: WebClient = helidonClient

  // Can make any request using the Helidon WebClient
  override def request(
      method: String, // GET, POST, PUT, etc.
      path: String, // The path to the endpoint (e.g. '/api/v1/endpoint')
      content: String = "", // Payload, can be empty
      queryParams: Map[String, String] = Map(), // Will be appended to the path
      headers: Map[String, String] = Map(), // Headers to be sent with the request
      log: Boolean = true,
      contentType: String = "application/json"
  ): Future[WookieeResponse] = {
    if (log) this.log.info(s"Sending [$method] to: [$path] with: [$content]")
    val withContentType = if (headers.contains("Content-Type")) headers else headers + ("Content-Type" -> contentType)

    WookieeWebClient.request(
      helidonClient,
      method,
      path,
      if (content == null) "" else content,
      withContentType,
      queryParams
    )
  }

  // Will extract the response into a case class
  def requestAndExtract[T: Manifest](
      method: String,
      path: String,
      content: String = "",
      queryParams: Map[String, String] = Map(),
      headers: Map[String, String] = Map(),
      log: Boolean = true,
      contentType: String = "application/json"
  )(implicit format: Formats): Future[T] =
    request(method, path, content, queryParams, headers, log, contentType)
      .map(WookieeWebClient.extractObject[T](_))
}
