package com.oracle.infy.wookiee.component.helidon.web.client

import io.helidon.common.reactive.Single
import io.helidon.webclient.{WebClient, WebClientRequestBuilder}
import java.net.{URI, URLDecoder}

object WookieeWebClient {

  def oneOff(host: String, path: String, method: String, payload: String, headers: Map[String, String]): String = {
    val withHeaders =
      headers.foldLeft(methodRequested(host, method))((builder, header) => builder.addHeader(header._1, header._2))
    val withQueryParams =
      getQueryParams(path).foldLeft(withHeaders)(
        (builder, queryParam) => builder.queryParam(queryParam._1, queryParam._2: _*)
      )

    val response = withQueryParams
      .path(path)
      .submit(payload, classOf[String])

    Single.create[String](response).await()
  }

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
