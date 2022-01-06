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
package com.oracle.infy.wookiee.logging

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging.{InitializeLogger, LoggerInitialized}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class LoggingActorSpec extends TestKit(ActorSystem("test", ConfigFactory.parseString( """logging.use-actor=off""")))
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  val logger: ActorRef = system.actorOf(Props[LoggingActor])

  "Logging" should {
    "test logging initialization" in {
      val probe = TestProbe()
      probe.send(logger, InitializeLogger(null))
      LoggerInitialized shouldBe probe.expectMsg(LoggerInitialized)
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
