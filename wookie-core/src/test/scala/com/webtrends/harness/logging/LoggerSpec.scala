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

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import ch.qos.logback.classic.Level
import com.webtrends.harness.TestKitSpecificationWithJUnit
import org.slf4j.LoggerFactory

class LoggerSpec extends TestKitSpecificationWithJUnit(ActorSystem("harness")) {

  val probe = new TestProbe(system)
  val appender = setupAppender
  sequential

  "logging" should {
    "allow for logging that is received by a mediator actor using Scala string interpolation" in {
      Logger.registerMediator(probe.ref)
      val logger = Logger("test")
      val x = 0
      logger.trace(s"testing ${x}123...")

      val msg = Trace(LoggerFactory getLogger "test", "testing 0123...", None, None, Nil, None)
      Logger.unregisterMediator(probe.ref)
      probe.expectMsgClass(classOf[Trace]) must be equalTo msg
    }

    "allow for logging that is received by a mediator actor using Java string interpolation" in {
      Logger.registerMediator(probe.ref)
      val logger = Logger("test")
      logger.debug("testing {}123...", 0)

      val msg = Debug(LoggerFactory getLogger "test", "testing {}123...", None, None, Seq(0), None)
      Logger.unregisterMediator(probe.ref)
      probe.expectMsgClass(classOf[Debug]) must be equalTo msg
    }

    "allow for logging that is handle directly by the underlying logging framework using Scala string interpolation" in {
      val logger = Logger("test")
      val x = 0
      logger.info(s"testing ${x}123...")
      appender.lastMessage.get must be equalTo "testing 0123..."
    }

    "allow for logging that is handle directly by the underlying logging framework using Java string interpolation" in {
      val logger = Logger("test")
      logger.warn("testing {}123...", 0)
      appender.lastMessage.get must be equalTo "testing 0123..."
    }

    "allow for logging that is handle directly by the underlying logging framework using Scala string interpolation and handles a Throwable" in {
      val logger = Logger("test")
      logger.error("testing {}123...", 0)
      appender.lastMessage.get must be equalTo "testing 0123..."
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }

  private def setupAppender(): TestingAppender = {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.ALL)
    val appender = new TestingAppender()
    appender.start
    root.addAppender(appender)
    appender
  }
}
