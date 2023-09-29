package com.oracle.infy.wookiee.kafka.produce

import com.oracle.infy.wookiee.kafka.WookieeKafka.{MessageData, WookieeRecord}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.ByteArraySerializer

import java.util.Properties

// This is a simple wrapper around a kafka producer with helper methods, create it using the apply in the companion object
// Example: val producer = WookieeKafkaProducer("localhost:9092")
case class WookieeKafkaProducer(bootstrapServers: String, extraProps: Properties = new Properties())
    extends AutoCloseable {
  protected val props = new Properties()
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
  props.putAll(extraProps)

  protected val producer = new KafkaProducer[Array[Byte], Array[Byte]](props)

  // Sends a message with a key and value to the topic
  def send(topic: String, key: Option[Array[Byte]], value: Array[Byte]): Unit = {
    val finalKey = key.getOrElse(Array())
    producer.send(new ProducerRecord[Array[Byte], Array[Byte]](topic, finalKey, value))
    ()
  }

  // Sends a message with a key and value to the topic, this signature enabled a callback function
  // that will be called after the message is sent
  def send(
      topic: String,
      key: Option[Array[Byte]],
      value: Array[Byte],
      callback: Either[Exception, MessageData] => Unit
  ): Unit = {
    val finalKey = key.getOrElse(Array())
    producer.send(
      new ProducerRecord[Array[Byte], Array[Byte]](topic, finalKey, value),
      (metadata: RecordMetadata, exception: Exception) => {
        Option(exception) match {
          case Some(ex) => callback(Left(ex))
          case None =>
            callback(
              Right(
                MessageData(
                  new String(finalKey),
                  new String(value),
                  metadata.offset(),
                  metadata.partition(),
                  metadata.topic(),
                  metadata.timestamp()
                )
              )
            )
        }
      }
    )
    ()
  }

  def send(topic: String, message: WookieeRecord): Unit =
    send(topic, Some(message.key), message.value)

  def send(topic: String, key: Option[String], value: String): Unit =
    send(topic, key.map(_.getBytes()), value.getBytes)

  def send(topic: String, key: Option[String], value: String, callback: Either[Exception, MessageData] => Unit): Unit =
    send(topic, key.map(_.getBytes()), value.getBytes, callback)

  // Close this producer
  def close(): Unit =
    producer.close()

  // Avoid using this unless absolutely needed (as it might change if our underlying tech changes)
  def underlying: KafkaProducer[Array[Byte], Array[Byte]] = producer
}
