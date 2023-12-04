package com.oracle.infy.wookiee.kafka.testing

import com.oracle.infy.wookiee.kafka.KafkaObjects.WookieeRecord
import com.oracle.infy.wookiee.kafka.consume.WookieeKafkaConsumer
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import com.oracle.infy.wookiee.kafka.testing.model._
import com.oracle.infy.wookiee.kafka.{KafkaObjects, WookieeKafka}
import org.apache.kafka.clients.admin.AdminClient

import java.util.concurrent.Executors
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.{Random, Try, Using}

// Meant to be used as an extensible trait but will work as an object as well
class KafkaTestHelper {

  // Use this execution context for any async operations
  val execContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool(Executors.defaultThreadFactory()))

  // When this method references kafkaPort, it will instantiate and start kafka and zookeeper
  def startKafka(): Unit =
    KafkaObjects.log.info(s"ZK started on port: $zkPort, Kafka started on port: $kafkaPort")

  def stopKafka(): Unit = {
    KafkaObjects.log.info(s"Stopping ZK on port [$zkPort], and Kafka on port [$kafkaPort]")
    if (adminClientReferenced)
      adminClient.close()
    kafkaServer.close()
    zkMode.close()
  }

  // Will generate a new random topic name so tests don't collide
  def getFreshTopicName: String =
    s"test-topic-${Try(getClass.getSimpleName).getOrElse("anonymous")}-${Random.nextInt(1000000)}"
  def autoCreateTopics: Boolean = true

  // Defaults to earliest offset, group id will be random
  def getKafkaConsumer: WookieeKafkaConsumer =
    getKafkaConsumer(resetOffsetsToLatest = false)

  def getKafkaConsumer(resetOffsetsToLatest: Boolean): WookieeKafkaConsumer =
    WookieeKafkaConsumer(
      s"localhost:$kafkaPort",
      s"group-id-test-${Random.nextInt(10000)}",
      resetToLatest = resetOffsetsToLatest
    )

  // Will send the given data to the test topic and return a consumer that can read from it
  def getSeededKafkaConsumer(
      testData: java.util.List[(Array[Byte], Array[Byte])],
      topic: String
  ): WookieeKafkaConsumer =
    getSeededKafkaConsumer(testData.asScala.toList, topic)

  def getSeededKafkaConsumer(
      testData: List[(Array[Byte], Array[Byte])],
      topic: String
  ): WookieeKafkaConsumer = {
    seedKafkaTopic(testData, topic)
    val consumer = getKafkaConsumer
    consumer.subscribe(topic)
    consumer
  }

  // Will produce the testData to the given topic and return the topic name
  def seedKafkaTopic(
      testData: java.util.List[(Array[Byte], Array[Byte])]
  ): String =
    seedKafkaTopic(testData, getFreshTopicName)

  def seedKafkaTopic(
      testData: java.util.List[(Array[Byte], Array[Byte])],
      topic: String
  ): String =
    seedKafkaTopic(testData.asScala.toList, topic)

  def seedKafkaTopic(
      testData: List[(Array[Byte], Array[Byte])],
      topic: String = getFreshTopicName
  ): String =
    Using(getKafkaProducer) { producer =>
      testData.foreach(data => producer.send(topic, Some(data._1), data._2))
      topic
    }.get // Close producer then throw any exceptions

  // Will poll on the consumer until the given number of events have been received
  def getNJavaEvents(numberOfEvents: Int, topic: String): java.util.List[WookieeRecord] =
    getNJavaEvents(numberOfEvents, topic, 15000L)

  def getNJavaEvents(numberOfEvents: Int, topic: String, timeout: Long): java.util.List[WookieeRecord] =
    Using(getKafkaConsumer)({ consumer =>
      getNJavaEvents(numberOfEvents, topic, consumer, timeout)
    }).get // Close consumer then throw any exceptions

  def getNJavaEvents(
      numberOfEvents: Int,
      topic: String,
      consumer: WookieeKafkaConsumer,
      timeout: Long
  ): java.util.List[WookieeRecord] =
    getNEvents(numberOfEvents, topic, consumer, timeout).asJava

  def getNEvents(numberOfEvents: Int, topic: String): List[WookieeRecord] =
    Using(getKafkaConsumer)({ consumer =>
      getNEvents(numberOfEvents, topic, consumer, 15000L)
    }).get // Close consumer then throw any exceptions

  def getNEvents(
      numberOfEvents: Int,
      topic: String,
      consumer: WookieeKafkaConsumer,
      timeout: Long
  ): List[WookieeRecord] = {
    if (!consumer.subscription().contains(topic))
      consumer.subscribe(topic)

    val records = new ListBuffer[WookieeRecord]()
    awaitEvent(
      {
        val recs = consumer.poll(1000L)
        if (recs.nonEmpty)
          KafkaObjects
            .log
            .info(s"Received records: [${recs.map(r => s"${r.getKey} -> ${r.getValue}").mkString(", ")}]")
        records ++= recs
        records.size >= numberOfEvents
      },
      timeout,
      ignoreError = false
    )
    records.toList
  }

  // Returns a started kafka producer ready to send
  def getKafkaProducer: WookieeKafkaProducer =
    WookieeKafka.startProducer(s"localhost:$kafkaPort")

  // Get a producer and consumer, and begin consuming on the given topic right away with the given 'process'
  def getProducerAndConsumer(
      groupId: String,
      topic: String,
      process: WookieeRecord => Unit,
      resetToLatest: Boolean = false
  ): (WookieeKafkaProducer, AutoCloseable) = {
    val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort")
    val consumer = WookieeKafka.startConsumerAndProcess(
      s"localhost:$kafkaPort",
      groupId,
      Seq(topic),
      process,
      resetToLatest = resetToLatest
    )(execContext)
    (producer, consumer)
  }

  // These lazy boys will wake up when called out in startKafka()
  lazy val zkMode: ZooMode = TestingServerMode()
  lazy val zkPort: Int = Try(zkMode.getConnectString.split(":").last.toInt).getOrElse(-1)

  lazy val (kafkaPort, kafkaServer): (Int, AutoCloseable) =
    WookieeKafka.startLocalKafkaServer(zkMode.getConnectString, autoCreateTopics = this.autoCreateTopics)

  @volatile private var adminClientReferenced = false

  // Won't instantiate unless referenced
  lazy val adminClient: AdminClient = {
    adminClientReferenced = true
    WookieeKafka.createAdminClient(s"localhost:$kafkaPort")
  }

  // Copied from ThreadUtil to avoid dependency
  // A helper method to wait for a true evaluation, great for testing
  def awaitEvent(f: => Boolean): Unit = awaitEvent(f, 15000L, ignoreError = true)

  def awaitEvent(
      f: => Boolean,
      waitMs: Long,
      ignoreError: Boolean
  ): Unit = {
    val goUntil = System.currentTimeMillis + waitMs
    while (System.currentTimeMillis < goUntil) {
      try {
        if (f)
          return
        else
          Thread.sleep(500L)
      } catch {
        case e: Exception =>
          if (ignoreError && System.currentTimeMillis < goUntil) {
            Thread.sleep(500L)
          } else {
            throw e
          }
      }
    }

    throw new RuntimeException("Timed out waiting for result")
  }
}
