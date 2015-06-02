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
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.HttpHeaders.{Origin, RawHeader}
import spray.http.{AllOrigins, HttpOrigin, SomeOrigins}
import spray.httpx._
import spray.testkit.Specs2RouteTest

class CORSDirectiveSpec extends SpecificationWithJUnit
    with Specs2RouteTest
    with CORSDirectives
    with RequestBuilding {

  "the 'corsFilter' directive" should {

    "respond with headers on request of '*'" in {
      Get("/test") ~> {
        corsFilter(AllOrigins) {
          complete("test")
        }
      } ~> check {
        header("Access-Control-Allow-Origin").get.value === "*"
        header("Access-Control-Allow-Credentials").get.value === "false"
      }
    }

    "respond with headers on request of 'http://www.webtrends.com'" in {
      Get("/test").withHeaders(List(Origin(Seq(HttpOrigin("http://www.webtrends.com"))))) ~> {
        corsFilter(SomeOrigins(Seq("http://www.webtrends.com"))) {
          complete("test")
        }
      } ~> check {
        header("Access-Control-Allow-Origin").get.value === "http://www.webtrends.com"
        header("Access-Control-Allow-Credentials").get.value === "false"
      }
    }

    "respond with headers on 'options' request of '*'" in {
      Get("/test") ~> {
        corsFilter(AllOrigins) {
          path(".*".r) {
            thePath =>
              respondWithHeaders(RawHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS, HEAD"),
                RawHeader("Access-Control-Allow-Headers", "authorization, content-type, origin, accept")) {

                respondWithStatus(200) {
                  complete("test")
                }
              }

          }
        }
      } ~> check {
        header("Access-Control-Allow-Methods").get.value === "POST, GET, PUT, DELETE, OPTIONS, HEAD"
        header("Access-Control-Allow-Headers").get.value === "authorization, content-type, origin, accept"
      }
    }

  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}
