package com.oracle.infy.wookiee.kafka.consume

import com.oracle.infy.wookiee.kafka.KafkaObjects.{
  AutoCloseableConsumer,
  WookieeOffset,
  WookieeRecord,
  WookieeTopicPartition
}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

// Scala-friendly wrapper for Kafka Consumer functionality
case class WookieeKafkaConsumer(
    bootstrapServers: String, // Comma-separated list of Kafka brokers
    groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
    enableAutoCommit: Boolean = true, // Auto-commit offsets? If false, can manually commit with consumer.commitSync()
    resetToLatest: Boolean = true, // If no committed offset is found, reset to the latest offset (default) or earliest?
    extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
) {
  private val hasBeenClosed: AtomicBoolean = new AtomicBoolean(false)

  protected val props = new Properties()
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
  props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
  props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit.toString)
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, if (resetToLatest) "latest" else "earliest")
  extraProps.forEach { (k, v) =>
    props.put(k, v)
    ()
  }

  protected val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](props)

  // What are the current assignments?
  def assignment(): Set[WookieeTopicPartition] =
    consumer.assignment().asScala.map(tp => (tp.topic(), tp.partition())).toSet

  // What are the current subscriptions?
  def subscription(): Set[String] =
    consumer.subscription().asScala.toSet

  // Method to subscribe to topics
  def subscribe(topics: String*): Unit =
    consumer.subscribe(topics.asJava)

  // Method to subscribe to topics using regex
  def subscribe(pattern: Pattern): Unit =
    consumer.subscribe(pattern)

  // Method to unsubscribe from topics
  def unsubscribe(): Unit =
    consumer.unsubscribe()

  // Method to poll records
  def poll(duration: Duration): Seq[WookieeRecord] = {
    val records = consumer.poll(duration)
    records
      .asScala
      .map { record =>
        WookieeRecord(
          record.key(),
          record.value(),
          Some(record.partition()),
          Some(record.offset()),
          Some(record.topic())
        )
      }
      .toSeq
  }

  // Method to poll messages and wrap them into WookieeMessages
  def poll(durationMillis: Long): Seq[WookieeRecord] =
    poll(Duration.ofMillis(durationMillis))

  // Start processing messages using this consumer
  // This method assumes we've already called subscribe(..) for a topic
  // Note: This could cause the group offset to update (depending on autoCommit settings)
  def processMessages(
      processMessage: WookieeRecord => Unit, // Each individual kafka message will go through this method
      pollMs: Long = 1000L // How long to wait for each batch of messages
  )(implicit ec: ExecutionContext): AutoCloseable =
    new AutoCloseableConsumer(this, processMessage, pollMs)

  // Method to assign partitions to consume from
  def assign(partitions: Seq[WookieeTopicPartition]): Unit =
    consumer.assign(partitions.map { case (topic, partition) => new TopicPartition(topic, partition) }.asJava)

  // Method to commit offsets
  def commitSync(): Unit =
    consumer.commitSync()

  // Method to commit synchronously
  def commitSync(offsets: Map[WookieeTopicPartition, WookieeOffset]): Unit = {
    val kafkaOffsets = offsets.map {
      case ((topic, partition), offset) =>
        new TopicPartition(topic, partition) -> new OffsetAndMetadata(offset)
    }
    consumer.commitSync(kafkaOffsets.asJava)
  }

  // Method to commit asynchronously
  def commitAsync(): Unit =
    consumer.commitAsync()

  // Method to seek to a specific offset
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def seek(partition: WookieeTopicPartition, offset: WookieeOffset): Unit = {
    val (topic, partitionId) = partition
    consumer.seek(new TopicPartition(topic, partitionId), offset)
  }

  // Method to seek to the beginning of a topic
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def seekToBeginning(partitions: Seq[WookieeTopicPartition]): Unit =
    consumer.seekToBeginning(partitions.map { case (topic, partition) => new TopicPartition(topic, partition) }.asJava)

  // Method to seek to the end of a topic
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def seekToEnd(partitions: Seq[WookieeTopicPartition]): Unit =
    consumer.seekToEnd(partitions.map { case (topic, partition) => new TopicPartition(topic, partition) }.asJava)

  // Method to get the last committed offset for a topic partition
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def position(partition: WookieeTopicPartition): WookieeOffset = {
    val (topic, partitionId) = partition
    consumer.position(new TopicPartition(topic, partitionId))
  }

  // Method to get a list of partitions for a topic
  def partitionsFor(topic: String): Seq[Int] =
    consumer.partitionsFor(topic).asScala.map(_.partition()).toSeq

  // Method to list the topics in the cluster
  def listTopics(): Set[String] =
    consumer.listTopics().keySet().asScala.toSet

  // Method to get the beginning offsets for a set of topic partitions
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def beginningOffsets(partitions: Seq[WookieeTopicPartition]): Map[WookieeTopicPartition, WookieeOffset] = {
    val kafkaPartitions = partitions.map { case (topic, partition) => new TopicPartition(topic, partition) }
    consumer
      .beginningOffsets(kafkaPartitions.asJava)
      .asScala
      .map {
        case (tp, offset) => (tp.topic() -> tp.partition()) -> (offset: WookieeOffset)
      }
      .toMap
  }

  // Method to get the end offsets for a set of topic partitions
  // NOTE: This method only seems to work when the topics were assigned, not subscribed
  def endOffsets(partitions: Seq[WookieeTopicPartition]): Map[WookieeTopicPartition, WookieeOffset] = {
    val kafkaPartitions = partitions.map { case (topic, partition) => new TopicPartition(topic, partition) }
    consumer
      .endOffsets(kafkaPartitions.asJava)
      .asScala
      .map {
        case (tp, offset) => (tp.topic() -> tp.partition()) -> (offset: WookieeOffset)
      }
      .toMap
  }

  // Method to close the consumer
  def close(): Unit = {
    consumer.close()
    hasBeenClosed.set(true)
  }

  // Method to check if the consumer has been closed
  def isClosed(): Boolean =
    hasBeenClosed.get()

  // Provide an escape hatch while keeping it internal
  def underlying: KafkaConsumer[Array[Byte], Array[Byte]] = consumer
}
