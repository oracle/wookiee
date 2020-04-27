/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.service.test

import java.net.ServerSocket

import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.component.Component
import com.webtrends.harness.service.Service
import scala.concurrent.duration._

// Add 'with WordSpecLike with MustMatchers' or 'with SpecificationLike' depending on scalatest/specs2
trait BaseWookieeTest {
  def config: Config = ConfigFactory.empty()
  def componentMap: Option[Map[String, Class[_<:Component]]] = None
  def servicesMap: Option[Map[String, Class[_<:Service]]] = None
  def logLevel: Level = Level.INFO
  def startupWait: FiniteDuration = 15.seconds
  def port: Int = freePort

  private val freePort: Int = getFreePort

  val testWookiee: TestHarness =
    TestHarness(config, servicesMap, componentMap, logLevel, startupWait, port)

  Thread.sleep(1000)
  implicit val system: ActorSystem = TestHarness.system(port).get

  def shutdown(): Unit =
    TestHarness.shutdown(port)

  def getFreePort: Int = {
    // Get an empty port for akka http websocket
    val socket = new ServerSocket(0)
    try {
      socket.setReuseAddress(true)
      socket.getLocalPort
    } finally if (socket != null) socket.close()
  }
}
