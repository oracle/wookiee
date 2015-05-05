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

import akka.actor.ActorRef
import akka.util.Timeout
import com.webtrends.harness.command.AddCommand
import com.webtrends.harness.component.socko.client._
import com.webtrends.harness.component.socko.command.TestCommand
import com.webtrends.harness.service.test.TestHarness
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the functionality around the Commands and the mixin socko traits
 *
 * @author Michael Cuthbert on 2/2/15.
 */
@RunWith(classOf[JUnitRunner])
class CommandSpec extends SockoTestBase {
  var sockoClient:Option[ActorRef] = None
  val commandManager = TestHarness.harness.get.commandManager.get
  commandManager ! AddCommand("TestCommand", classOf[TestCommand])
  // wait a second for the command to be registered in the CommandManager
  Thread.sleep(1000)

  sequential

  def getClient : ActorRef = {
    sockoClient match {
      case Some(sc) => sc
      case None =>
        val d = Duration(2L, "seconds")
        implicit val timeout = Timeout(d)
        val a = actorSystem.actorSelection(sockoManager.path.toStringWithoutAddress + "/" + SockoClient.SockoClientName).resolveOne()
        sockoClient = Some(Await.result(a, d))
        sockoClient.get
    }
  }

  "Commands with Socko Mixin Traits" should {
    "handle a GET request" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/testcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("GET")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
    }

    "handle a POST request" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/testcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
    }

    "handle a PUT request" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/testcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("PUT")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
    }

    "handle a HEAD request" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/testcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("HEAD")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
    }

    "handle a OPTIONS request" in {
      val url = new URL("http://127.0.0.1:8080/_wt_internal/testcommand")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("OPTIONS")
      val resp = getResponseContent(conn)

      resp.status mustEqual "200"
    }
  }
}
