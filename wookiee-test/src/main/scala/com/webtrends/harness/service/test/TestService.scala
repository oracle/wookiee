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
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.policy.PolicyManager
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.{GetMetaDetails, Ready}
import com.webtrends.harness.service.meta.{ServiceMetaData, ServiceMetaDetails}
import com.webtrends.harness.service.test.command.TestCommand
import com.webtrends.harness.service.test.policy.TestPolicy

import scala.concurrent.Future

class TestService extends Service with ShutdownListener {
  var metaData: Option[ServiceMetaData] = None

  override def checkHealth: Future[HealthComponent] = {
    val comp = HealthComponent("testservice", ComponentState.NORMAL, "test")
    comp.addComponent(HealthComponent("childcomponent", ComponentState.DEGRADED, "test"))
    Future[HealthComponent] {
      comp
    }
  }

  // Define the receive function
  override def serviceReceive = shutdownReceive orElse {
    case Ready =>
      sender() ! Ready
      log.info("I am now ready: " + self.path)
    case Ready(meta) =>
      metaData = Some(meta)
      log.info("I am now ready, meta data set: " + self.path)
    case GetMetaDetails => sender ! ServiceMetaDetails(supportsHttp = false)
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addPolicies: Unit = {
    PolicyManager.addPolicy(TestPolicy.PolicyName, TestPolicy)
  }

}

object TestService {
  var gotMessage: Boolean = false
}