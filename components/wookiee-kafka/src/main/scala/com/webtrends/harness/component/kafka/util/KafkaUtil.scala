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

import java.util.Properties

import com.typesafe.config.{Config, ConfigValueType}
import kafka.api.{PartitionOffsetRequestInfo, _}
import kafka.common.TopicAndPartition
import kafka.consumer.SimpleConsumer

object KafkaUtil {
  def getFetchRequest(clientName: String, topic: String, part: Int, offset: Long, fetchSize: Int): FetchRequest = {
    val req: FetchRequest = new FetchRequestBuilder().clientId(clientName).addFetch(topic, part, offset, fetchSize).build()
    req
  }

  def getDesiredAvailableOffset(consumer: SimpleConsumer, topic: String, partition: Int, desiredStartOffset: Long, clientName: String): Long = {
    val topicAndPartition: TopicAndPartition = new TopicAndPartition(topic, partition)

    val startOffsetRequest = OffsetRequest(
      Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.EarliestTime, 1)),
      replicaId = 0) //TODO: determine replicaId, if needed?

    val startResponse: OffsetResponse = consumer.getOffsetsBefore(startOffsetRequest)
    if (startResponse.hasError) {
      throw new IllegalStateException("Kafka response error getting earliest offsets" )
    }
    val startOffsets: Seq[Long] = startResponse.partitionErrorAndOffsets(topicAndPartition).offsets
    if (startOffsets.length != 1) {
      throw new IllegalStateException(s"Expect one earliest offset but got [${startOffsets.length}]")
    }


    val endOffsetRequest = OffsetRequest(
      Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.LatestTime, 1)),
      replicaId = 0)  //TODO: determine replicaId, if needed?

    val endResponse: OffsetResponse = consumer.getOffsetsBefore(endOffsetRequest)
    if (endResponse.hasError) {
      throw new IllegalStateException("Kafka response error getting latest offsets" ) //+ startResponse.error())
    }
    val endOffsets: Seq[Long] = endResponse.partitionErrorAndOffsets(topicAndPartition).offsets
    if (endOffsets.length != 1) {
      throw new IllegalStateException(s"Expect one latest offset but got [${endOffsets.length}]")
    }

    val range:Array[Long] = new Array(2)

    range(0) = startOffsets.head
    range(1) = endOffsets.head

    if (range(1) < range(0)) {
      range(0) = range(1)
    }
    if (desiredStartOffset > range(0) && desiredStartOffset < range(1)) {
      range(0) = desiredStartOffset
    }

    range(0)

  }

  def getSmallestAvailableOffset(consumer: SimpleConsumer, topic: String, partition: Int): Long = {
    val topicAndPartition: TopicAndPartition = new TopicAndPartition(topic, partition)

    val endOffsetRequest = OffsetRequest(
      Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.EarliestTime, 1)),
      replicaId = 0) //TODO: determine replicaId, if needed?

    val endResponse: OffsetResponse = consumer.getOffsetsBefore(endOffsetRequest)
    if (endResponse.hasError) {
      throw new IllegalStateException("Kafka response error getting earliest offsets" )
    }
    val endOffsets: Seq[Long] = endResponse.partitionErrorAndOffsets(topicAndPartition).offsets
    if (endOffsets.length != 1) {
      throw new IllegalStateException(s"Expect one earliest offset but got [${endOffsets.length}]")
    }
    endOffsets.head
  }

  def getLargestAvailableOffset(consumer: SimpleConsumer, topic: String, partition: Int): Long = {
    val topicAndPartition: TopicAndPartition = new TopicAndPartition(topic, partition)

    val startOffsetRequest = OffsetRequest(
      Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.LatestTime, 1)),
      replicaId = 0) //TODO: determine replicaId, if needed?

    val startResponse: OffsetResponse = consumer.getOffsetsBefore(startOffsetRequest)
    if (startResponse.hasError) {
      throw new IllegalStateException("Kafka response error getting earliest offsets" )
    }
    val startOffsets: Seq[Long] = startResponse.partitionErrorAndOffsets(topicAndPartition).offsets
    if (startOffsets.length != 1) {
      throw new IllegalStateException(s"Expect one earliest offset but got [${startOffsets.length}]")
    }

    startOffsets.head
  }

  def configToProps(config: Config): Properties = {
    val props: Properties = new Properties
    import scala.collection.JavaConversions._
    config.entrySet.foreach { entry =>
      entry.getValue.valueType match {
        case ConfigValueType.STRING =>
          props.put(entry.getKey, config.getString(entry.getKey))
        case ConfigValueType.NUMBER =>
          props.put(entry.getKey, config.getNumber(entry.getKey).toString)
        case _ =>
        // Ignore other types
      }
    }
    props.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer")
    props
  }
}
