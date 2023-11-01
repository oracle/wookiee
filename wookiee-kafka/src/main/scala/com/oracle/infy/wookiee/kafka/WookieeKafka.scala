package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.consume.WookieeKafkaConsumer
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.test.TestHarness
import kafka.server.KafkaConfig._
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.joda.time.DateTime
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import java.util.regex.Pattern
import java.util.{Optional, Properties}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Random, Try}

object WookieeKafka extends LoggingAdapter {
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

  def startProducer(bootstrapServers: String): WookieeKafkaProducer =
    startProducer(bootstrapServers, new Properties())

  def startProducer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      extraProps: Properties // Extra properties to pass to the Kafka producer
  ): WookieeKafkaProducer =
    WookieeKafkaProducer(bootstrapServers, extraProps)

  def startConsumer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String // Consumer group ID, set to unique if this consumer should see all messages
  ): WookieeKafkaConsumer =
    startConsumer(bootstrapServers, groupId, enableAutoCommit = true)

  def startConsumer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
      enableAutoCommit: Boolean, // Auto-commit offsets? If false, can manually commit with consumer.commitSync()
      resetToLatest: Boolean = true, // If no committed offset is found, reset to the latest offset (default) or earliest?
      extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
  ): WookieeKafkaConsumer =
    WookieeKafkaConsumer(bootstrapServers, groupId, enableAutoCommit, resetToLatest, extraProps)

  // Start a consumer that automatically processes messages with the provided function
  def startConsumerAndProcess(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
      topics: Seq[String], // Takes a list of exact topics to consume
      processMessage: WookieeRecord => Unit, // Each individual kafka message will go through this method
      pollMs: Long = 1000L, // How long to wait for each batch of messages
      enableAutoCommit: Boolean = true, // Auto-commit offsets? If false, can manually commit with wookieeRecord.commitOffsets()
      resetToLatest: Boolean = true, // If no committed offset is found, reset to the latest offset (default) or earliest?
      extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
  )(implicit ec: ExecutionContext): AutoCloseable = {
    val consumer = startConsumer(bootstrapServers, groupId, enableAutoCommit, resetToLatest, extraProps)
    consumer.subscribe(topics: _*)
    new AutoCloseableConsumer(consumer, processMessage, pollMs)
  }

  // Start a consumer that automatically processes messages with the provided function
  def startConsumerAndProcessPattern(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
      topicPattern: Pattern, // Takes a pattern for topic consumption
      processMessage: WookieeRecord => Unit, // Each individual kafka message will go through this method
      pollMs: Long = 1000L, // How long to wait for each batch of messages
      enableAutoCommit: Boolean = true, // Auto-commit offsets? If false, can manually commit with wookieeRecord.commitOffsets()
      resetToLatest: Boolean = true, // If no committed offset is found, reset to the latest offset (default) or earliest?
      extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
  )(implicit ec: ExecutionContext): AutoCloseable = {
    val consumer = startConsumer(bootstrapServers, groupId, enableAutoCommit, resetToLatest, extraProps)
    consumer.subscribe(topicPattern)
    new AutoCloseableConsumer(consumer, processMessage, pollMs)
  }

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

  // For Java interop
  def createTopic(
      adminClient: AdminClient,
      topic: String
  ): (Boolean, Int, Int) =
    createTopic(adminClient, topic, compacted = false, None, None)

  // This is a helper function to create a kafka topic
  // The return value is a tuple containing (compacted, partitions, replication)
  def createTopic( // Creates topic in kafka
      adminClient: AdminClient, // Can get this via createAdminClient method in this class
      topic: String,
      compacted: Boolean, // If true, this topic will be compacted (persistent)
      partitions: Option[Int] = None, // If None, kafka server will use its default config
      replication: Option[Int] = None // If None, kafka server will use its default config
  ): (Boolean, Int, Int) = {
    def toJavaOptional[T](option: Option[Any]): Optional[T] = option match {
      case Some(x) => Optional.of(x.asInstanceOf[T])
      case None    => Optional.empty()
    }

    val newTopic = new NewTopic(
      topic,
      toJavaOptional[java.lang.Integer](partitions),
      toJavaOptional[java.lang.Short](replication.map(_.toShort))
    ).configs(
      Map(
        "cleanup.policy" -> s"${if (compacted) "compact" else "delete"}"
      ).asJava
    )
    val topicCreated = adminClient.createTopics(Seq(newTopic).asJava)
    topicCreated.all().get()
    val actualPartitions = topicCreated.numPartitions(topic).get()
    val actualReplication = topicCreated.replicationFactor(topic).get()
    (compacted, actualPartitions, actualReplication)
  }

  // This is a helper function to create a kafka AdminClient
  // bootstrapServers -- comma separated list of kafka brokers
  def createAdminClient(bootstrapServers: String): AdminClient = {
    // Kafka admin client for topic management
    val adminProps = new Properties()
    adminProps.put("bootstrap.servers", bootstrapServers)
    AdminClient.create(adminProps)
  }

  // Easy-use methods for Java interop
  def startLocalKafkaServer(
      zkConnStr: String // Can get this via `new TestingServer(zkPort).getConnectString`
  ): (Int, AutoCloseable) =
    startLocalKafkaServer(zkConnStr, None, false)

  def startLocalKafkaServer(
      zkConnStr: String, // Can get this via `new TestingServer(zkPort).getConnectString`
      kafkaPort: Int, // Set this if you've already reserved a port for kafka
      autoCreateTopics: Boolean // If false, topics will need to be manually made via the createTopic method in this class
  ): (Int, AutoCloseable) =
    startLocalKafkaServer(zkConnStr, Option(kafkaPort), autoCreateTopics)

  def startLocalKafkaServer(
      zkConnStr: String, // Can get this via `new TestingServer(zkPort).getConnectString`
      autoCreateTopics: Boolean // If false, topics will need to be manually made via the createTopic method in this class
  ): (Int, AutoCloseable) =
    startLocalKafkaServer(zkConnStr, None, autoCreateTopics)

  // This is a helper function to start a local Kafka server for testing
  // Returns the hosting kafka port and the KafkaServer instance
  def startLocalKafkaServer(
      zkConnStr: String, // Can get this via `new TestingServer(zkPort).getConnectString`
      kafkaPort: Option[Int], // If kafkaPort is None, we'll pick a random free port and return it
      // If false, topics will need to be manually made via the createTopics method in this class
      autoCreateTopics: Boolean = false
  ): (Int, AutoCloseable) = {
    // So that we don't need kafka as a runtime dep
    import kafka.server.{KafkaConfig, KafkaServer}

    // Setup Local Kafka Broker
    val props = new Properties()
    val kafkaDir: String = s"/tmp/kafka_dir-$kafkaPort-${DateTime.now.getMillis}"
    val port = kafkaPort.getOrElse(TestHarness.getFreePort)

    props.put(BrokerIdProp, Random.nextInt(100).toString)
    props.put("host.name", "localhost")
    props.put("advertised.host.name", "localhost")
    props.put("port", port)
    props.put("advertised.port", port.toString)
    props.put("log.dir", kafkaDir)
    props.put(ZkConnectProp, zkConnStr)
    props.put(OffsetsTopicReplicationFactorProp, "1")
    props.put(ReplicaSocketTimeoutMsProp, "1500")
    props.put(ListenersProp, s"PLAINTEXT://localhost:$port")
    props.put(AutoCreateTopicsEnableProp, s"$autoCreateTopics")

    val kafkaSettings = new KafkaConfig(props)

    val server = new KafkaServer(kafkaSettings)
    server.startup()
    (port, () => {
      server.shutdown()
      server.awaitShutdown()
    })
  }
}
