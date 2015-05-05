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

package com.webtrends.service

import com.webtrends.harness.component.socko.route.SockoRouteHandler
import org.joda.time.{DateTimeZone, DateTime}
import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.routes.{Path, GET}

import scala.collection.mutable
import scala.concurrent.Future

/**
 * @author Michael Cuthbert on 1/26/15.
 */
class SockoPing extends SockoRouteHandler {

  /**
   * Checks if this handler matches the route in the event, returned map will be used to handle request
   * or pass None back if this is not the right path
   *
   * @param event Event receive by Socko
   * @return None- path does not match, Some(map)- path matches, extract variables
   */
  override def matchSockoRoute(event: HttpRequestEvent): Option[mutable.HashMap[String, AnyRef]] = {
    event match {
      case GET(Path("/custom/ping")) => Some(new mutable.HashMap[String, AnyRef]())
      case _ => None
    }
  }

  /**
   * Attempts to handle the Socko route
   *
   * @param bean The http request's data
   * @return true if route was handled, false otherwise
   */
  override def handleSockoRoute(event:HttpRequestEvent, bean: mutable.HashMap[String, AnyRef]): Unit = {
    val dtime = new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString()
    event.response.write("socko pong from httpexample: ".concat(dtime))
  }
}
