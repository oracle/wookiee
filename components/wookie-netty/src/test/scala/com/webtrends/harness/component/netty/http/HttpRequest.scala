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

package com.webtrends.harness.component.netty.http

import java.net.{HttpURLConnection, URL}

import scala.io.Source


/**
 * Created by wallinm on 1/15/15.
 */
object HttpRequest {
  def Get(targetURL: String) : String = {
    val url = new URL (targetURL)
    val connection = url.openConnection.asInstanceOf[HttpURLConnection]
    
    connection.setRequestMethod ("GET")
    connection.setRequestProperty ("Content-Type", "text/plain")
    connection.setRequestProperty ("Content-Language", "en-US")
    connection.setConnectTimeout(3000)

    connection.connect

    //Get Response
    val is = connection.getInputStream
    val response = Source.fromInputStream(is).mkString
    connection.disconnect

    response
  }
}
