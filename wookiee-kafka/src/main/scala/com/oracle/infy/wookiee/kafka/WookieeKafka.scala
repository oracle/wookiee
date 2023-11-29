package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.KafkaObjects.{AutoCloseableConsumer, WookieeRecord}
import com.oracle.infy.wookiee.kafka.consume.WookieeKafkaConsumer
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import kafka.server.KafkaConfig._
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.joda.time.DateTime

import java.net.ServerSocket
import java.util.regex.Pattern
import java.util.{Optional, Properties}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.{Random, Using}

object WookieeKafka {
  /* Producer Methods */

  def startProducer(bootstrapServers: String): WookieeKafkaProducer =
    startProducer(bootstrapServers, new Properties())

  def startProducer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      extraProps: Properties // Extra properties to pass to the Kafka producer
  ): WookieeKafkaProducer =
    WookieeKafkaProducer(bootstrapServers, extraProps)

  /* Consumer Methods */

  def startConsumer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String // Consumer group ID, set to unique if this consumer should see all messages
  ): WookieeKafkaConsumer =
    startConsumer(bootstrapServers, groupId, enableAutoCommit = true)

  def startConsumer(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
      enableAutoCommit: Boolean, // Auto-commit offsets? If false, can manually commit with consumer.commitSync()
      resetToLatest: Boolean = false, // If no committed offset is found, reset to the latest offset (default) or earliest?
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
      resetToLatest: Boolean = false, // If no committed offset is found, reset to the latest offset (default) or earliest?
      extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
  )(implicit ec: ExecutionContext): AutoCloseable = {
    val consumer = startConsumer(bootstrapServers, groupId, enableAutoCommit, resetToLatest, extraProps)
    consumer.subscribe(topics: _*)
    consumer.processMessages(processMessage, pollMs)
  }

  // Start a consumer that automatically processes messages with the provided function
  def startConsumerAndProcessPattern(
      bootstrapServers: String, // Comma-separated list of Kafka brokers
      groupId: String, // Consumer group ID, set to unique if this consumer should see all messages
      topicPattern: Pattern, // Takes a pattern for topic consumption
      processMessage: WookieeRecord => Unit, // Each individual kafka message will go through this method
      pollMs: Long = 1000L, // How long to wait for each batch of messages
      enableAutoCommit: Boolean = true, // Auto-commit offsets? If false, can manually commit with wookieeRecord.commitOffsets()
      resetToLatest: Boolean = false, // If no committed offset is found, reset to the latest offset (default) or earliest?
      extraProps: Properties = new Properties() // Extra properties to pass to the Kafka consumer
  )(implicit ec: ExecutionContext): AutoCloseable = {
    val consumer = startConsumer(bootstrapServers, groupId, enableAutoCommit, resetToLatest, extraProps)
    consumer.subscribe(topicPattern)
    consumer.processMessages(processMessage, pollMs)
  }

  /* Admin Methods */

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

  /* Server Methods */

  // Easy-use methods for Java interop
  def startLocalKafkaServer(
      zkConnStr: String // Can get this via `new TestingServer(zkPort).getConnectString`
  ): (Int, AutoCloseable) =
    startLocalKafkaServer(zkConnStr, None)

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
    val port = kafkaPort.getOrElse(getFreePort)

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

  // Convenience method to find a free port
  def getFreePort: Int =
    Using(new ServerSocket(0)) { socket =>
      socket.setReuseAddress(true)
      socket.getLocalPort
    }.get
}
