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

package com.webtrends.harness.component.socko

import java.net.{HttpURLConnection, URL}

import com.webtrends.harness.command.AddCommand
import com.webtrends.harness.component.socko.handlers.{TestSockoHandler, TestSockoHandler2}
import com.webtrends.harness.component.socko.route.SockoRouteManager
import com.webtrends.harness.service.test.TestHarness
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * This Spec tests functionality around the RoutePath, which matches the incoming url's with custom paths
 * 1. Test just the basic /foo path used by Socko Path function
 * 2. Test /foo2 that matches the TestSockoHandler2 route
 * 3. Test /foo/$var that matches a route in TestSockoHandler2 and responds with "bar" and places var in commandbean
 * 4. Test /foo/$var same as above just with different $var value
 * 5. Test /foo/$var but have the path unmatched using /foo/variant1/test
 * 6. Test /foo/$var1/$var2 to make sure that both values are placed in the bean
 */
//@RunWith(classOf[JUnitRunner])
class RoutePathSpec extends SockoTestBase {
  val port = 8080
  val path = "http://127.0.0.1:" + port + "/"
  // add the route handlers
  val commandManager = TestHarness.harness.get.commandManager.get
  commandManager ! AddCommand("route1handler", classOf[TestSockoHandler])
  commandManager ! AddCommand("route2handler", classOf[TestSockoHandler2])
  Thread.sleep(1000)

  "Test handlers" should {
    "be able to add Socko Handlers to the Socko Route Manager" in {
      SockoRouteManager.getHandler("testHandler1") match {
        case Some(s) => true mustEqual true
        case None => false mustEqual true
      }
    }

    "handle the get path /foo" in {
      val url = new URL(path + "foo")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content mustEqual "bar"
    }

    "handle the get path /foo2" in {
      val url = new URL(path + "foo2")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content mustEqual "path2-"
    }

    "handle the get path /foo/$var where $var is some variable 'variant1'" in {
      val url = new URL(path + "foo/variant1")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content mustEqual "path1-variant1"
    }

    "handle the get path /foo/$var where $var is some variable 'variant2'" in {
      val url = new URL(path + "foo/variant2")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content mustEqual "path1-variant2"
    }

    "not handle the get path /foo/$var/test/$var" in {
      val url = new URL(path + "foo/variant1/test/variant2")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      println(resp.content)
      resp.status mustEqual "404"
    }

    "handle the get path /foo/$var1/$var2 where $var1 & $var2 is some variable 'variant1|2'" in {
      val url = new URL(path + "foo/variant1/variant2")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content.length must be > 0
      resp.content mustEqual "path3-variant1,variant2"
    }
  }
}
