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
/*package com.webtrends.harness.component.http

import akka.testkit.{TestKit, TestProbe}
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.StatusCodes
import spray.httpx.RequestBuilding
import spray.testkit.Specs2RouteTest

class MetricsDirectivesSpec extends SpecificationWithJUnit
    with Specs2RouteTest
    with RequestBuilding
    with MetricsDirectives {

  val metric = Timer("group.subgroup.name.scope")

  "the metrics directives" should {

    "allow for timer to return a response" in {
      val probe = new TestProbe(system)
      MetricsEventBus.subscribe(probe.ref)
      implicit val service = new MetricsAdapter {}

      Get("/test") ~> time(metric) {
        complete("good")
      } ~> check {
        status === StatusCodes.OK
      }
    }

    "allow for timing a call" in {
      val probe = new TestProbe(system)
      MetricsEventBus.subscribe(probe.ref)
      implicit val service = new MetricsAdapter {}

      Get("/test") ~> time(metric) {
        complete("good")
      }
      probe.expectMsgClass(classOf[TimerObservation])
      success
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}*/
