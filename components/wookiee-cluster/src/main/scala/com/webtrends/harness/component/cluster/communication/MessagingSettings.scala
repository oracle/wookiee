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

package com.webtrends.harness.component.cluster.communication

import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.utils.ConfigUtil

/*
 * @author cuthbertm on 10/22/14 9:22 AM
 */
class MessagingSettings (config: Config = ConfigFactory.load) {
  protected val c: Config = ConfigUtil.prepareSubConfig(config, "message-processor")

  var ShareInterval         = c getMilliseconds      "share-interval"
  var TrashInterval         = c getMilliseconds      "trash-interval"

  require(ShareInterval > 0, "share-interval must be set")
  require(TrashInterval > 0, "trash-interval must be set")
}

object MessagingSettings {
  implicit def apply(config: Config = ConfigFactory.load()) = new MessagingSettings(config)
}
