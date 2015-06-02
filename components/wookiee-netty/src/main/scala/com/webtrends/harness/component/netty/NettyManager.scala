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
package com.webtrends.harness.component.netty

import com.webtrends.harness.app.HarnessActor.SystemReady
import com.webtrends.harness.component.netty.config.NettyServerSettings
import com.webtrends.harness.component.{ComponentStarted, Component}
import com.webtrends.harness.utils.ConfigUtil

case class NettyRunning()

class NettyManager(name:String) extends Component(name)
  with NettyServer {

  implicit val serverSettings = NettyServerSettings(ConfigUtil.prepareSubConfig(config, name))

  override protected def defaultChildName: Option[String] = Some(NettyServer.Name)

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive = super.receive orElse {
    case NettyRunning => context.parent ! ComponentStarted(self.path.name)
    case SystemReady =>
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    startNettyServer(serverSettings)
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    super.stop
    stopNettyServer
  }
}

object NettyManager {
  val ComponentName = "wookiee-netty"
}