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

import java.net.InetAddress

import akka.actor.{ActorSystem, Address, ExtendedActorSystem, Extension}

class SystemExtension(system: ExtendedActorSystem) extends Extension {
  def address: Address = system.provider.getDefaultAddress
}

object SystemExtension {

  def getAddress(system: ActorSystem): String = {
    val ext = new SystemExtension(system.asInstanceOf[ExtendedActorSystem])
    ext.address.host.getOrElse(InetAddress.getLocalHost.getCanonicalHostName)
  }
}
