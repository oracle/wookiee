package com.oracle.infy.wookiee.component.helidon.web.util

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.zip.{DataFormatException, Inflater}
import scala.io.Source

object WebUtil {

  // Helper method to get query params from a URL
  def getQueryParams(url: String): Map[String, String] =
    getQueryParams(new URI(url))

  def getQueryParams(uri: URI): Map[String, String] = {
    Option(uri.getQuery) match {
      case Some(query) =>
        query
          .split("&")
          .flatMap { param =>
            param.split("=") match {
              case Array(key, value) => Some(key -> value)
              case _                 => None
            }
          }
          .toMap
      case None => Map.empty
    }
  }
}
