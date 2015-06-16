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

package com.webtrends.harness.component.socko.utils

import com.webtrends.harness.component.socko.route.SockoHeader

/**
 * @author Michael Cuthbert on 1/27/15.
 */
object SockoUtils {

  val KeyAllRequestMethods = "all"

  /**
   * Based on the method this function will filter out only the required headers from the map
   *
   * @param method the method for the request
   * @param headers the full list of headers
   * @return
   */
  def toSockoHeaderFormat(method:String, headers:Map[String, List[SockoHeader]], size: Int = 0) : Map[String, String] = {
    val retHeaders = (headers filter {
      x => x._1.equalsIgnoreCase(method) || x._1 == KeyAllRequestMethods
    } values).flatten.toList
    toSockoHeaderFormat(retHeaders :+ SockoHeader("Content-Length", size + ""))
  }

  /**
   * Takes a list of SockoHeaders and converts it to a Map[String, String], which is what Socko expects when
   * writing out the response
   *
   * @param headers the list of SockoHeaders
   * @return
   */
  def toSockoHeaderFormat(headers:List[SockoHeader]) : Map[String, String] = (headers map { h => h.headerName -> h.headerValue }).toMap

  /**
   * Does a little normalization of content type strings. So will convert stuff like JSON to application/json.
   *
   * @param contentType The content type you want normalized
   * @return
   */
  def normalizeContentType(contentType:String) : String = {
    // this should probably get a list of content types from a file and then normalize based on the file
    if (contentType.equalsIgnoreCase("json")) {
      "application/json"
    } else if (contentType.equalsIgnoreCase("text")) {
      "text/plain"
    } else if (contentType.equalsIgnoreCase("html")) {
      "text/html"
    } else {
      contentType
    }
  }
}
