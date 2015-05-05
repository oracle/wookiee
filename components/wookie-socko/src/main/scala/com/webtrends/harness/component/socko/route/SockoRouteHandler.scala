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

package com.webtrends.harness.component.socko.route

import org.mashupbots.socko.events.HttpRequestEvent

import scala.collection.mutable

trait SockoRouteHandler {
  /**
   * Checks if this handler matches the route in the event, returned map will be used to handle request
   * or pass None back if this is not the right path
   *
   * @param event Event receive by Socko
   * @return None- path does not match, Some(map)- path matches, extract variables
   */
  def matchSockoRoute(event:HttpRequestEvent): Option[mutable.HashMap[String, AnyRef]]

  /**
   * Attempts to handle the Socko route
   *
   * @param event Event received by Socko
   * @param bean The http request's data
   * @return true if route was handled, false otherwise
   */
  def handleSockoRoute(event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef])
}
