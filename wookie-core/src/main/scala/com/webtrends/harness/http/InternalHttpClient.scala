/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.http

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URLConnection}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

/**
 * Contains common methods used to call our web server
 */
trait InternalHttpClient {

  /**
   * Get standard response form the supplied connection
   *
   * @param conn HTTP Connection
   */
  def getResponseContent(conn: HttpURLConnection): HttpResponseData = {
    try {
      val connIn = conn.getInputStream
      val headers = collection.mutable.Map.empty[String, String]
      val statusLine = getResponseHeaders(conn, headers)

      val content = new StringBuilder
      if (connIn != null) {
        var in: BufferedReader = null
        val contentEncoding = headers.getOrElse("Content-Encoding", "")
        if (contentEncoding == "") {
          in = new BufferedReader(new InputStreamReader(connIn))
        } else {
          if (contentEncoding == "gzip") {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(connIn)))
          } else if (contentEncoding == "deflate") {
            in = new BufferedReader(new InputStreamReader(new InflaterInputStream(connIn)))
          } else {
            throw new UnsupportedOperationException("Content encoding: " + contentEncoding)
          }
        }

        readAll(in, content)
        in.close()
      }

      HttpResponseData(statusLine, content.toString(), headers)
    } catch {
      case ex: Throwable =>
        System.out.println(ex.toString)
        getResponseErrorContent(conn)
    }
  }

  /**
   * Get error response in the event that response status is not 200
   *
   * @param conn HTTP Connection
   */
  def getResponseErrorContent(conn: HttpURLConnection): HttpResponseData = {
    val content = new StringBuilder()
    if (conn.getErrorStream != null) {
      val in = new BufferedReader(new InputStreamReader(conn.getErrorStream))
      readAll(in, content)
      in.close()
    }

    val headers = collection.mutable.Map.empty[String, String]
    val statusLine = getResponseHeaders(conn, headers)

    HttpResponseData(statusLine, content.toString(), headers)
  }

  /**
   * Read in headers
   *
   * @param conn HTTP Connection
   * @param headers Collection of header name-values
   */
  def getResponseHeaders(conn: URLConnection, headers: collection.mutable.Map[String, String]): String = {
    headers.clear()
    var statusLine = ""
    var i = 0
    while (i >= 0) {
      val name = conn.getHeaderFieldKey(i)
      val value = conn.getHeaderField(i)

      if (name == null && value == null) {
        i = -1
      } else if (name == null) {
        // Value must be the response code like "HTTP/1.1 404 Not Found"
        statusLine = value
        i += 1
      } else {
        headers.put(name, value)
        i += 1
      }
    }

    statusLine
  }

  private def readAll(in: BufferedReader, sb: StringBuilder) = {
    var finished = false
    while (!finished) {
      val str = in.readLine()
      if (str == null) {
        sb.setLength(sb.length - 1) //Remove last \n
        finished = true
      } else {
        sb.append(str + "\n")
      }
    }
  }

  /**
   * Data returned from the HTTP connection
   */
  case class HttpResponseData(
                               statusLine: String,
                               content: String,
                               headers: collection.mutable.Map[String, String]) {

    private val startIndex = statusLine.indexOf(" ") + 1
    val status = statusLine.substring(startIndex, startIndex + 3)

    override def toString: String = {
      val sb = new StringBuilder
      sb.append(statusLine + "\n")
      headers.foreach(m => sb.append(m._1 + "=" + m._2 + "\n"))
      sb.append("\n" + content + "\n")
      sb.toString()
    }

  }
}
