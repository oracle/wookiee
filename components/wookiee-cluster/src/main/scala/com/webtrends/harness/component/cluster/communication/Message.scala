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

import java.net.InetAddress

/**
 * @author vonnagyi on 3/11/13 11:26 AM
 */
case class Message(val topic: String, body: Any) extends MessageHeader {
  val timestamp = System.currentTimeMillis
}

object Message {

  private lazy val host = InetAddress.getLocalHost.getHostName

  /**
   * Create a message
   * @param topic The message topic
   * @param msg The object to pass along with the message
   * @return An instance of a Message
   */
  def createMessage(topic: String, msg: Any): Message = Message(topic, msg)
}
