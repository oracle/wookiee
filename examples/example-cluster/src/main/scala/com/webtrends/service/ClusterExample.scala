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

package com.webtrends.service

import java.net.InetAddress

import com.webtrends.harness.component.cluster.communication.{Message, MessagingAdapter}
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.Ready
import org.joda.time.DateTime

import scala.util.{Failure, Success}

case class TestMessage(message: String, timestamp: DateTime)

class ClusterExample extends Service with ActorLoggingAdapter with MessagingAdapter {

  /**
   * Subscribe to the topics for which we are going to listen
   */
  override def preStart: Unit = {
    subscribe("cluster-topic", self, false)
    subscribe("cluster-topic-with-response", self, false)
    subscribe("internal-topic", self, true)
    super.preStart
  }

  /**
   * Unsubscribe from the topics to which we were listening
   */
  override def postStop: Unit = {
    unsubscribe("cluster-topic", self)
    unsubscribe("cluster-topic-with-response", self)
    unsubscribe("internal-topic", self)
  }

  /**
   * Process messages.
   */
  override def serviceReceive = ({
    case Ready(meta) =>
      log.info("Ready message received, start sending messages")
      val thread = new Thread {
        override def run: Unit = {
          sendMessages
        }
      }
      thread.start()

    case Message("cluster-topic", msg) =>
      msg match {
        case TestMessage(message, timestamp) => log.info("Received cluster-topic{ message: " + message + " sent: " + timestamp + " }")
      }

    case Message("cluster-topic-with-response", msg) =>
      msg match {
        case TestMessage(message, timestamp) =>
          log.info("Received cluster-topic-with-response{ message: " + message + " sent: " + timestamp + " }")
          sender ! "an answer"
      }

    case Message("internal-topic", msg) =>
      msg match {
        case TestMessage(message, timestamp) =>
          log.info("Received internal-topic){ message: " + message + " sent: " + timestamp + " }")
      }
  }: Receive) orElse super.serviceReceive

  /**
   * Send messages to our topics every minute
   */
  def sendMessages = {
    while (true) {
      val msg =
      // Send the message to the topic, only one subscriber will pick it up
      send("cluster-topic", TestMessage("This is a test message", DateTime.now))
      // Send the message to the topic and expect a response within five seconds
      sendWithFuture("cluster-topic-with-response", TestMessage("This is a message that is expecting a response", DateTime.now))(5000).onComplete {
        case Success(answer) => log.info("    Answer: " + answer)
        case Failure(fail) => log.info("Failed to get a response to cluster-topic-with-response")
      }
      // This message should only be acted upon by this cluster node
      send("internal-topic", TestMessage("This is a message from " + InetAddress.getLocalHost, DateTime.now))

      // Published messages go to all subscribers with no response
      publish("cluster-topic",  TestMessage("This is a published message", DateTime.now))

      Thread sleep 60000
    }
  }
}

