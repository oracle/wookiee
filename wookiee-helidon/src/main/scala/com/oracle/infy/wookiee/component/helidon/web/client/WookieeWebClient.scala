package com.oracle.infy.wookiee.component.helidon.web.client

import io.helidon.common.http.DataChunk
import io.helidon.common.reactive.Single
import io.helidon.webclient.{WebClient, WebClientRequestBuilder, WebClientResponse}

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

// Useful client for interacting with any endpoint, powered by Helidon
object WookieeWebClient {

  // Will make a single call to a given endpoint, with the provided payload, headers, etc.
  def oneOff(
      host: String,
      path: String,
      method: String,
      payload: String,
      headers: Map[String, String]
  ): WebClientResponse = {
    val withHeaders =
      headers.foldLeft(methodRequested(host, method))((builder, header) => builder.addHeader(header._1, header._2))
    val withQueryParams =
      getQueryParams(path).foldLeft(withHeaders)(
        (builder, queryParam) => builder.queryParam(queryParam._1, queryParam._2: _*)
      )

    val responseRef = new AtomicReference[WebClientResponse]()
    val data = DataChunk.create(payload.getBytes(StandardCharsets.UTF_8))

    val _ = withQueryParams
      .path(path)
      .submit(Single.just(data)) // submit the payload here
      .thenAccept(response => {
        responseRef.set(response) // store the response
      })
      .await()

    responseRef.get() // return the stored response
  }

  // Useful method to get the actual content from a response
  def getContent(response: WebClientResponse): String =
    response.content().as(classOf[String]).await()

  // Translates the method string to a Helidon WebClientRequestBuilder
  def methodRequested(host: String, method: String): WebClientRequestBuilder = {
    // This client doesn't need to be shutdown
    val client = WebClient
      .builder()
      .baseUri(host)
      .build()

    method.toUpperCase match {
      case "GET"     => client.get()
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
  def getQueryParams(url: String): Map[String, List[String]] = {
    val query = new URI(url).getQuery
    if (query == null) {
      Map.empty
    } else {
      query
        .split("&")
        .toList
        .map { param =>
          val pair = param.split("=").map(URLDecoder.decode(_, "UTF-8"))
          pair(0) -> pair(1)
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toMap
    }
  }
}
