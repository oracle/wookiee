package com.oracle.infy.wookiee.component.helidon.web.client

import io.helidon.common.reactive.Single
import io.helidon.webclient.{WebClient, WebClientRequestBuilder}

object WookieeWebClient {

  def oneOff(host: String, path: String, method: String, payload: String, headers: Map[String, String]): String = {
    val response = methodRequested(host, method)
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
}
