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

package com.webtrends.harness.component.socko.config

import com.webtrends.harness.service.test.config.TestConfig

/**
 * @author Michael Cuthbert on 1/29/15.
 */
object SockoTestConfig {
  val config = TestConfig.conf("""
      wookiee-socko {
        server-name=Webtrends Harness
        hostname="localhost,127.0.0.1"
        port=8080

        num-handler-routees=5

        static-content {
          rootPaths = []
          serverCacheMaxFileSize = 0
          serverCacheTimeoutSeconds = 0
          browserCacheTimeoutSeconds = 0
          type = "file"
        }
      }
      akka.actor.deployment {
        /system/component/wookiee-socko/Socko/socko-base {
          router = round-robin
          nr-of-instances = 3
        }
        /system/component/wookiee-socko/static-content-handler {
          router = round-robin
          nr-of-instance = 3
        }
      }
  """)
}
