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
package com.webtrends.harness.logging

import akka.actor.{ActorSystem, Props}
import akka.event.Logging.{InitializeLogger, LoggerInitialized}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.TestKitSpecificationWithJUnit

class LoggingActorSpec extends TestKitSpecificationWithJUnit(ActorSystem("test", ConfigFactory.parseString( """logging.use-actor=off"""))) {

  val logger = system.actorOf(Props[LoggingActor])

  "Logging" should {
    "test logging initialization" in {
      val probe = TestProbe()
      probe.send(logger, InitializeLogger(null))
      LoggerInitialized must beEqualTo(probe.expectMsg(LoggerInitialized))
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}
