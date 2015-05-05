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
import java.nio.charset.Charset

import com.webtrends.harness.command.AddCommand
import com.webtrends.harness.component.socko.command.MarshallCommand
import com.webtrends.harness.service.test.TestHarness
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * @author Michael Cuthbert on 2/2/15.
 */
@RunWith(classOf[JUnitRunner])
class MarshallCommandSpec extends SockoTestBase {
  val commandManager = TestHarness.harness.get.commandManager.get
  commandManager ! AddCommand("MarshallCommand", classOf[MarshallCommand])
  // wait a second for the command to be registered in the CommandManager
  Thread.sleep(1000)

  sequential

  "Commands that return non JSON/String based data" should {
    "marshall objects correctly for GET" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/marshallcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("GET")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content mustEqual "String,1,65.0,78"
    }

    "marshall objects correctly for POST" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/marshallcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content mustEqual "String,1,65.0,78"
    }

    "marshall objects correctly for PUT" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/marshallcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("PUT")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content mustEqual "String,1,65.0,78"
    }

    "unmarshall objects correctly for POST" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/marshallcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      sendPostRequest(conn, "text/plain", Charset.forName("UTF-8"), "test,1,1.0,1")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content mustEqual "test,1,1.0,1"
    }

    "unmarshall objects correctly for PUT" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/marshallcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      sendPutRequest(conn, "text/plain", Charset.forName("UTF-8"), "test,1,1.0,1")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
      resp.content mustEqual "test,1,1.0,1"
    }
  }
}
