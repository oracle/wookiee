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

package com.webtrends.harness.component.kafka.util

import kafka.message.{MessageSet, MessageAndOffset}

class KafkaMessageSet(val ms: MessageSet, offset: Long) extends Iterable[MessageAndOffset] {
  val messageSet = ms.filter{ msg => msg.offset >= offset }
  override def iterator: Iterator[MessageAndOffset] = messageSet.iterator
}

object KafkaMessageSet {
  def apply(ms: MessageSet, offset: Long) = new KafkaMessageSet(ms, offset)
}
