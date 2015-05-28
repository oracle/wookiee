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

package com.webtrends.harness.component.kafka.config

import com.typesafe.config.ConfigFactory
import com.webtrends.harness.service.test.config.TestConfig

/**
 * @author woods
 *         1/13/15
 */
object KafkaTestConfig {
  val config = TestConfig.conf("""
        wookiee-kafka {
          app-name = "test"
          enabled = true
          consumer {
            topics = [
              {
                name = "topicName1"
                event-age-threshold-seconds = 10
              },
              {
                name = "topicName2"
                event-age-threshold-seconds = 30
              }
            ]

            assignment-distributor {
              assignment-refresh-seconds = 2
              fetch-timeout-millis = 501
            }
          }
          worker-class = "com.webtrends.harness.component.kafka.actor.TestPartitionWorker"
        }

        wookiee-zookeeper {
          datacenter = "Lab"
          pod = "Tests"
          quorum = "gzoo01.staging.dmz"
          session-timeout = 30s
          connection-timeout = 30s
          retry-sleep = 5s
          retry-count = 150
          base-path = "/discovery/clusters"
          message-processor {
            # How often the MessageProcessor should share it's subscription information
            share-interval = 1s
            # When should MessageTopicProcessor instances be removed after there are no longer any subscribers for that topic
            trash-interval = 30s
            # The default send timeout
            default-send-timeout = 2s
          }
        }
       """).resolve()


  def zkConfig(zkConnection: String ) = {
    ConfigFactory.parseString(
        s"""wookiee-zookeeper.quorum ="$zkConnection" """
      ).withFallback(config).resolve()
  }
}
