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

package com.webtrends.harness.component.spray.directive

import akka.testkit.TestKit
import com.webtrends.harness.command.CommandBean
import org.specs2.mutable.SpecificationWithJUnit
import spray.http._
import spray.httpx.RequestBuilding
import spray.testkit.Specs2RouteTest

/**
 * @author Michael Cuthbert on 12/12/14.
 */
class CommandDirectiveSpec extends SpecificationWithJUnit
    with Specs2RouteTest
    with CommandDirectives
    with RequestBuilding {

  val pathMap = Map(
    "path1" -> "/test/path1",
    "path2" -> "/path1/test",
    "path3" -> "/varpath/$var1/$var2"
  )

  val testHeaders = Map(
    HttpMethods.GET.name -> List(HttpHeaders.`Access-Control-Allow-Methods`(HttpMethods.GET)),
    HttpMethods.POST.name -> List(HttpHeaders.`Access-Control-Allow-Methods`(HttpMethods.POST)),
    CommandDirectives.KeyAllHeaders -> List(HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`))
  )
  // for the empty list case
  val testHeadersEmpty = Map[String, List[HttpHeader]]()

  "The commandPath directive" should {
    "respond OK on valid path" in {
      Get("/test/path") ~> {
        commandPath("/test/path/") {
          bean => complete("test")
        }
      } ~> check {
        status == StatusCodes.OK
      }
    }

    "reject invalid path" in {
      Get("/test/path") ~> {
        commandPath("/test/path2/") {
          bean => complete("test")
        }
      } ~> check {
        handled must beFalse
      }
    }

    "respond with string SEGMENT url in bean" in {
      Get("/test/seg/path") ~> {
        commandPath("/test/$key/path") {
          bean => complete(s"${bean.get("key").get}")
        }
      } ~> check {
        status == StatusCodes.OK
        body.asString == "seg"
      }
    }

    "respond with multiple string SEGMENT url in bean" in {
      Get("/test/seg/path/seg2") ~> {
        commandPath("/test/$key1/path/$key2") {
          bean => complete(s"${bean.get("key1").get}-${bean.get("key2").get}")
        }
      } ~> check {
        status == StatusCodes.OK
        body.asString == "seg-seg2"
      }
    }

    "respond with int SEGMENT url in bean" in {
      Get("/test/1234/path") ~> {
        commandPath("/test/$key/path") {
          bean =>
            bean.get("key").get match {
              case x:Integer => complete(x.toString)
              case _ => complete("NA")
            }
        }
      } ~> check {
        status == StatusCodes.OK
        body.asString == "1234"
      }
    }

    "reject invalid path with SEGMENT" in {
      Get("/test/path") ~> {
        commandPath("/test/$key/path") {
          bean => complete("test")
        }
      } ~> check {
        handled must beFalse
      }
    }

    "respond OK with valid options path" in {
      Get("/test/path1") ~> {
        commandPath("/test/path1|path2") {
          bean => complete("test")
        }
      } ~> check {
        handled must beTrue
      }
    }

    "reject invalid options path" in {
      Get("/test/path1") ~> {
        commandPath("/test/path2|path3") {
          bean => complete("test")
        }
      } ~> check {
        handled must beFalse
      }
    }
  }

  "The commandPaths directive" should {
    "reject any non-matching paths" in {
      Get("/test/path3") ~> {
        commandPaths(pathMap) {
          bean => complete("path3")
        }
      } ~> check {
        handled must beFalse
      }
    }

    "accept any matching paths" in {
      Get("/path1/test") ~> {
        commandPaths(pathMap) {
          bean => complete(bean.get(CommandBean.KeyPath).get.toString)
        }
      } ~> check {
        body.asString == "path2"
        handled must beTrue
      }
    }

    "do variable substitution on matching path" in {
      Get("/varpath/test1/1") ~> {
        commandPaths(pathMap) {
          bean => complete(s"${bean.get(CommandBean.KeyPath).get}-${bean.get("var1").get}-${bean.get("var2").get}")
        }
      } ~> check {
        body.asString == "path3-test1-1"
        handled must beTrue
      }
    }
  }

  "The command header directive" should {
    "Get request should respond with GET header" in {
      Get("/test") ~> {
        mapHeaders(testHeaders) {
          complete("headers")
        }
      } ~> check {
        val rH = header("Access-Control-Allow-Methods") match {
          case Some(s) =>
            val headerMethods = s.asInstanceOf[HttpHeaders.`Access-Control-Allow-Methods`].methods
            headerMethods.contains(HttpMethods.GET) && !headerMethods.contains(HttpMethods.POST)
          case None => false
        }
        rH must beTrue
        val rH2 = header("Cache-Control") match {
          case Some(s) => s.asInstanceOf[HttpHeaders.`Cache-Control`].directives.contains(CacheDirectives.`no-cache`)
          case None => false
        }
        rH2 must beTrue
      }
    }

    "Post request should respond with Options header" in {
      Post("/test") ~> {
        mapHeaders(testHeaders) {
          complete("headers")
        }
      } ~> check {
        val rH = header("Access-Control-Allow-Methods") match {
          case Some(s) =>
            val headerMethods = s.asInstanceOf[HttpHeaders.`Access-Control-Allow-Methods`].methods
            !headerMethods.contains(HttpMethods.GET) && headerMethods.contains(HttpMethods.POST)
          case None => false
        }
        rH must beTrue
        val rH2 = header("Cache-Control") match {
          case Some(s) => s.asInstanceOf[HttpHeaders.`Cache-Control`].directives.contains(CacheDirectives.`no-cache`)
          case None => false
        }
        rH2 must beTrue
      }
    }

    "Get request with empty header set should succeed" in {
      Get("/test") ~> {
        mapHeaders(testHeadersEmpty) {
          complete("headers")
        }
      } ~> check {
        handled must beTrue
      }
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}
