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

package com.oracle.infy.wookiee.component.zookeeper

import java.util.concurrent.atomic.AtomicInteger

import org.apache.curator.x.discovery.{ProviderStrategy, ServiceInstance}
import org.apache.curator.x.discovery.details.InstanceProvider
import scala.jdk.CollectionConverters._

class WookieeWeightedStrategy extends ProviderStrategy[WookieeServiceDetails] {
  private val index = new AtomicInteger(0)

  def getInstance(instanceProvider: InstanceProvider[WookieeServiceDetails]): ServiceInstance[WookieeServiceDetails] = {
    val instances = instanceProvider.getInstances.asScala

    if (instances.isEmpty) {
      null
    } else if (instances.map(x => x.getPayload.getWeight).toSet.size == 1) {
      roundRobin(instances)
    } else {
      val headWeight = instances.sortBy(x => x.getPayload.getWeight).head.getPayload.getWeight
      roundRobin(instances.filter(x => x.getPayload.getWeight == headWeight))
    }
  }

  def roundRobin(
      instances: scala.collection.mutable.Buffer[ServiceInstance[WookieeServiceDetails]]
  ): ServiceInstance[WookieeServiceDetails] = {
    val thisIndex = Math.abs(this.index.getAndIncrement())
    val size = instances.size
    instances(thisIndex % size)
  }
}
