/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.logging

import akka.http.scaladsl.model.{DateTime, StatusCode}
import com.oracle.infy.wookiee.component.akkahttp.routes.AkkaHttpRequest
import com.oracle.infy.wookiee.logging.LoggingAdapter
import org.slf4j.Logger

object AccessLog extends LoggingAdapter {
  val host: String = java.net.InetAddress.getLocalHost.getHostName
  val accessLog: Logger = log

  def logAccess(request: AkkaHttpRequest, accessLogId: String, statusCode: StatusCode): Unit = {

    // modify the logback.xml file to write the "AccessLog" entries to a file without all of the prefix information
    try {
      val status: String = statusCode.intValue.toString
      val responseTimestamp: Long = System.currentTimeMillis()
      val requestTimestamp: Long = request.time
      val elapsedTime: Long = responseTimestamp - requestTimestamp
      val requestTime: String = DateTime(requestTimestamp).toIsoDateTimeString()
      val origin = request.requestHeaders.getOrElse("origin", "-")
      val userAgent = request.requestHeaders.getOrElse("user-agent", "-")

      /*
          LogFormat "%h %l %u %t \"%r\" %>s %b %{ms} %o %uaT"

          %h – The IP address of the server.
          %l – The identity of the client determined by identd on the client’s machine. Will return a hyphen (-) if this information is not available.
          %u – The id of the client if the request was authenticated.
          %t – The time that the request was received, in UTC
          \"%r\" – The request line that includes the HTTP method used, the requested resource path, and the HTTP protocol that the client used.
          %>s – The status code that the server sends back to the client.
          %b – The size of the object requested. Will return a hyphen (-) if this information is not available.
          %{ms}T - The time taken to serve the request, in milliseconds
          %o - The Origin sent in request header. If the origin header not there, it returns hyphen (-).
          %ua - The User Agent sent in request header. If user agent not there, returns hyphen (-).


          see https://httpd.apache.org/docs/2.4/logs.html
       */
      accessLog.info(s"""${AccessLog.host} - $accessLogId [$requestTime] "${request
        .method
        .value} ${request.path} ${request.protocol.value}" $status - $elapsedTime - $origin - $userAgent""")

    } catch {
      case e: Exception =>
        accessLog.error("Could not construct access log", e)
    }
  }
}
