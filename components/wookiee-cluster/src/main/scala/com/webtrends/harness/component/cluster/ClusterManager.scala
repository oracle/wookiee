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

import com.webtrends.harness.component.Component
import com.webtrends.harness.component.cluster.communication.Messaging
import com.webtrends.harness.component.zookeeper.Zookeeper
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.utils.ConfigUtil

/**
 * Important to note that if you use clustering you must not include the Zookeeper component
 * as Clustering will start it up independently, and zookeeper config must be within the clustering config
 */
class ClusterManager(name:String) extends Component(name)
    with Clustering
    with Zookeeper
    with Messaging {

  private val akkaProvider = context.system.settings.config.getString("akka.actor.provider")
  implicit val clusterSettings = ClusterSettings(ConfigUtil.prepareSubConfig(config, name), akkaProvider)
  private val zookeeperConfig = ConfigUtil.prepareSubConfig(config, "wookiee-zookeeper")
  implicit val zookeeperSettings = ZookeeperSettings(zookeeperConfig)

  override protected def defaultChildName: Option[String] = Some(Messaging.MessagingName)

  override def start = {
    startZookeeper(true)
    startClustering
    startMessaging
    super.start
  }

  override def stop = {
    super.stop
    stopClustering
  }
}

object ClusterManager {
  val ComponentName = "wookiee-cluster"
}
