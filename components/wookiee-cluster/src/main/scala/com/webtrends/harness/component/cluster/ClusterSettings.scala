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
package com.webtrends.harness.component.cluster

import com.typesafe.config.Config

case class ClusterSettings (
  basePath: String = "",
  randomSeedNodes: Int = 3,
  persistLeader: Boolean = false) {

  /**
   * This is the clustering version and tracks along the lines of breakable clustering. By using
   * this value as part of the registration path we can make sure that harness instances
   * can talk to with other nodes with compatible cluster versions.
   */
  val clusterVersion = "1.1"

  require(!basePath.isEmpty, "base-path must be set")
  require(randomSeedNodes > 0, "trash-interval must be set")
}

object ClusterSettings {
  def apply(config: Config, akkaProvider:String): ClusterSettings = {
    // If clustering is enabled then the provider must be 'ClusterActorRefProvider',
    // otherwise if not clustering then the provider must be 'ClusterActorRefProvider', otherwise it is not valid.
    require( if(akkaProvider.equalsIgnoreCase("akka.cluster.ClusterActorRefProvider")) {
      true
    }
    else if (akkaProvider.equalsIgnoreCase("akka.remote.RemoteActorRefProvider") ||
      akkaProvider.equalsIgnoreCase("akka.actor.LocalActorRefProvider")) {
      true
    }
    else {
      false},
        "If clustering is enabled then the provider must be 'ClusterActorRefProvider', otherwise if not clustering then the provider must be 'RemoteActorRefProvider', otherwise it is not valid." )

    ClusterSettings(config getString "base-path",
                    config getInt "random-seed-nodes",
                    config getBoolean "persist-leader")
  }
}
