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

package com.webtrends.harness.service.test

import akka.util.Timeout
import com.webtrends.harness.app.HarnessActor.{PrepareForShutdown, SystemReady}
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.component.{Component, ComponentResponse}
import com.webtrends.harness.service.test.TestSystemActor.RegisterShutdownListener

import scala.concurrent.duration._

object TestComponent {
  val ComponentMessage = "Test component ready"
}

/**
 * woods
 * 12/31/14
 */
class TestComponent(name:String) extends Component(name) with ShutdownListener {
  implicit val timeout = Timeout(2 seconds)

  /**
   * Components will typically receive a couple type of messages
   * 1. Start - Will execute after all components have loaded
   * 2. Stop - Will execute prior to shutdown of the harness
   *
   * @return
   */
  override def receive: Receive = super.receive orElse shutdownReceive orElse {
      case StatusRequest => sender ! TestComponent.ComponentMessage
      case SystemReady => log.info("Test component ready for duty")
    }
}
