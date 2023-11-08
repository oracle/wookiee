package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.component.web.http.HttpObjects.WookieeRequest
import com.oracle.infy.wookiee.logging.LoggingAdapter

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

// Will be called after each request to log the access
object AccessLog extends LoggingAdapter {
  val host: String = java.net.InetAddress.getLocalHost.getHostName

  def logAccess(request: Option[WookieeRequest], method: String, path: String, status: Int): Unit = {
    // Don't log default endpoint requests
    if (path.contains("favicon.ico") || path.contains("healthcheck") || path.contains("metrics")) return

    val responseTimestamp: Long = System.currentTimeMillis()
    val requestTimestamp: Long = request.map(_.getCreatedTime).getOrElse(responseTimestamp)
    val elapsedTime: Long = responseTimestamp - requestTimestamp
    val instant = Instant.ofEpochMilli(requestTimestamp)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.of("UTC"))

    val requestTime: String = formatter.format(instant)
    val origin = request.map(_.headers.getStringValue("origin")).getOrElse("-")
    val userAgent = request.map(_.headers.getStringValue("user-agent")).getOrElse("-")

    /*
        LogFormat "%h %t \"%r\" %>s %{ms}T %o %ua"

        %h – The IP address of the server.
        %t – The time that the request was received, in UTC
        \"%r\" – The request line that includes the HTTP method used, and the requested resource path.
        %>s – The status code that the server sends back to the client.
        %{ms}T - The time taken to serve the request, in milliseconds
        %o - The Origin sent in request header. If the origin header not there, it returns hyphen (-).
        %ua - The User Agent sent in request header. If user agent not there, returns hyphen (-).
     */
    log.info(s"""$host - [$requestTime] "$method $path" $status - $elapsedTime - $origin - $userAgent""")
  }
}
