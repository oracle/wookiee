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

package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.component.spray.route.RouteManager
import org.specs2.mutable.SpecificationWithJUnit
import spray.routing.{HttpService, Directives}
import spray.testkit.Specs2RouteTest

/**
 * @author Michael Cuthbert on 12/19/14.
 */
class BaseSprayRoutesSpecs extends SpecificationWithJUnit with Directives with Specs2RouteTest with HttpService {
  def actorRefFactory = system

  val testCommandRef = TestActorRef[BaseTestCommand]
  val testActor = testCommandRef.underlyingActor
  val testCustomCommandRef = TestActorRef[CustomTestCommand]
  val testCustomActor = testCustomCommandRef.underlyingActor

  "Test Command" should {
    "should handle Get requests using SprayGet" in {
      Get("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
        handled must beTrue
      }
    }

    "should not handle Post requests using SprayGet" in {
      Post("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
        handled must beFalse
      }
    }

    "should handle Get requests with different keys using SprayGet" in {
      Get("/foo/1234/bar/5678") ~> RouteManager.getRoute("BaseTest_get").get ~> check {
        handled must beTrue
      }
    }

    "should handle custom requests from Command using SprayCustom" in {
      Get("/foo/bar") ~> RouteManager.getRoute("CustomTest_custom").get ~> check {
        handled must beTrue
      }
    }

    "should handle Head requests using SprayHead" in {
      Head("/foo/key1/bar/kye2") ~> RouteManager.getRoute("BaseTest_head").get ~> check {
        handled must beTrue
      }
    }

    "should handle Options requests using SprayOption" in {
      Options("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_options").get ~> check {
        handled must beTrue
      }
    }

    "should handle Patch requests using SprayPatch" in {
      Patch("/foo/key1/bar/key2") ~> RouteManager.getRoute("BaseTest_patch").get ~> check {
        handled must beTrue
      }
    }
  }
}
