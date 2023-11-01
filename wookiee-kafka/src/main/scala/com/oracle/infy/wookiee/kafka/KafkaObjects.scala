package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.consume.WookieeKafkaConsumer
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

object KafkaObjects {
  private[oracle] val log: Logger = LoggerFactory.getLogger(this.getClass)

  type WookieeTopicPartition = (String, Int) // (Topic, Partition)
  type WookieeOffset = Long // Offset

  object WookieeRecord {

    def apply(key: String, value: String): WookieeRecord =
      new WookieeRecord(key.getBytes, value.getBytes)
  }

  // This is a simple wrapper around a kafka message with helper methods
  // This object will be used by both the producer and consumer
  case class WookieeRecord(
      key: Array[Byte],
      value: Array[Byte],
      partition: Option[Int] = None,
      offset: Option[Long] = None,
      topic: Option[String] = None
  ) {
    def getKey: String = new String(key)

    def getValue: String = new String(value)

    def jsonKey: JValue = parse(getKey)

    def jsonValue: JValue = parse(getValue)

    // Will be filled in by the consumer
    def commitOffsets(): Unit =
      log.warn("commitOffsets() not implemented for this message")
  }

  // For callbacks in the producer
  case class MessageData(key: String, value: String, offset: Long, partition: Int, topic: String, timestamp: Long)

  protected[oracle] class AutoCloseableConsumer(
      consumer: WookieeKafkaConsumer,
      processMessage: WookieeRecord => Unit,
      pollMs: Long
  )(implicit ec: ExecutionContext)
      extends AutoCloseable {

    def logPollingError(ex: Throwable): Unit =
      log.error("Error in `wookiee-kafka` while polling for messages.", ex)

    @volatile private var shouldRun = true

    // Start consuming and processing messages in a separate thread
    Future {
      while (shouldRun) {
        try {
          // Get next batch
          val records: Seq[WookieeRecord] = consumer.poll(pollMs)

          // Process messages
          val allProcessed: Seq[Try[Unit]] = records.map(record => Try(processMessage(record)))

          // Log failures
          allProcessed.foreach {
            case Failure(exception) =>
              log.error("Error while processing message.", exception)
            case _ => // Message processed successfully
          }
        } catch {
          case ex: Throwable =>
            logPollingError(ex)
        }
      }
      consumer.close()
    }

    override def close(): Unit = {
      shouldRun = false
    }
  }
}
