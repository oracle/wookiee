wookiee-kafka
=============

This is a Kafka support library for Wookiee. Its main purpose is to wrap and extend
the most common Kafka interactions into a simple to use API. It has ways to easily
create Kafka producers and consumers, and can spin up a local server for testing.

Wookiee does not need to be running for this library to function.

## Usage
### Kafka Server
For testing purposes, you can spin up a local Kafka server by adding the following
code to any codebase. Note that by default we turn auto.topic.creation off, so you
will need to manually create topics if you want to use them. See the `Create Topics` section:
```scala
import com.oracle.infy.wookiee.kafka.WookieeKafka

val (kafkaPort: Int, closeable: AutoCloseable) = WookieeKafka.startLocalKafkaServer(
  "zk.server.path:2181" // ZK quorum, can spin one up via curator-test library
)
// The kafka service will now be on "localhost:$kafkaPort"

// Or, if needing extra settings
val (kafkaPort2: Int, closeable2: AutoCloseable) = WookieeKafka.startLocalKafkaServer(
  "zk.server.path:2181", // Can get this via `new TestingServer(zkPort).getConnectString`
  Some(9092), // If kafkaPort is None, we'll pick a random free port and return it
  // If false, topics will need to be manually made via the createTopics method in this class
  autoCreateTopics = false
)
```

### Create Topics
If you need to create topics, you can do so via the `createTopics` method in the
`WookieeKafka` object. Like so:

```scala
import com.oracle.infy.wookiee.kafka.WookieeKafka

val adminClient = WookieeKafka.createAdminClient(
  "kafka.server.path:9092" // Kafka server, can spin one up via startLocalKafkaServer method
)

WookieeKafka.createTopic(
  adminClient, // Admin client from above
  "topic-name" // Topic to create
)
```

### Kafka Producer
To create a Kafka producer, you can use the `startProducer` method in the
`WookieeKafka` object. Like so:

```scala
import com.oracle.infy.wookiee.kafka.WookieeKafka

val producer = WookieeKafka.startProducer(
  "kafka.server.path:9092" // Kafka server, can spin one up via startLocalKafkaServer method
)
producer.send("topic-name", "some-message")
```
Explore the `KafkaProducer` class for more options to send messages.

### Kafka Consumer (Object Oriented)
To create a Kafka consumer, you can use the `startConsumer` method in the
`WookieeKafka` object. Like so:

```scala
import com.oracle.infy.wookiee.kafka.WookieeKafka

val consumer = WookieeKafka.startConsumer(
  "kafka.server.path:9092", // Kafka server, can spin one up via startLocalKafkaServer method
  "group-id" // Group ID for the consumer
)
consumer.subscribe("topic-name")
val messages: Seq[WookieeRecord] = consumer.poll(1000L)
```
Explore the `KafkaConsumer` class for more options to consume messages.

### Kafka Consumer (Functional)
To create a Kafka Consumer and immediately start funneling records through a supplied function, 
you can use the functional `startConsumer` method in the `WookieeKafka` object. Like so:

```scala
import com.oracle.infy.wookiee.kafka.WookieeKafka
import com.oracle.infy.wookiee.kafka.WookieeKafka.WookieeRecord

val closeable: AutoCloseable = WookieeKafka.startConsumerAndProcess(
  s"kafka.server.path:9092",
  "group-id",
  Seq(s"topic-name"), 
  { record: WookieeRecord =>
    log.info(s"Received message [${new String(record.value)}]")
  }
)

// When you want to close it up
closeable.close()
```

### Kafka Testing Support
To test Kafka consumers and producers, you can use the `KafkaTestHelper` trait.
NOTE: By default the `KafkaTestHelper` class will use the same topic name for all methods, be careful to avoid collisions if not specifying a specific topic name across multiple unit tests.

```scala
import com.oracle.infy.wookiee.kafka.testing.KafkaTestHelper

class MyTest extends KafkaTestHelper with SomeUnitTestSuite {
  // Adapt this to your testing framework
  def beforeAll(): Unit =
    startKafka()
  
  // Adapt this to your testing framework
  def afterAll(): Unit =
    stopKafka()
  
  
  
  test("seed a kafka topic, get a subscribed consumer back, expect 3 messages from it") {
    val testTopic = getFreshTopicName
    val consumer = getSeededKafkaConsumer(
      List(
        ("key1".getBytes, "value1".getBytes),
        ("key2".getBytes, "value2".getBytes),
        ("key3".getBytes, "value3".getBytes)
      ), testTopic
    )

    // Could be more than 3 events due to other tests
    val records = getNEvents(3, testTopic, consumer, 15000L)
    records.size mustEqual 3
  }
  
  test("same test as above but without passing around a consumer") {
    val testTopic = seedKafkaTopic(
      List(
        ("key1".getBytes, "value1".getBytes),
        ("key2".getBytes, "value2".getBytes),
        ("key3".getBytes, "value3".getBytes)
      )
    )

    val records = getNEvents(3, testTopic)
    records.size mustEqual 3
  }

  // Defaults to true, but can be overridden to require manual topic creation
  override def autoCreateTopics = true
    
  // Optional: If using slf4j-api this trick will suppress the crazy amount of logs kafka/zk produce
  Try { // Set logger to INFO level
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    loggerContext.getLoggerList.forEach(logger => logger.setLevel(Level.INFO))
  }
}
```