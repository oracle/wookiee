package com.oracle.infy.wookiee.kafka.testing

import com.oracle.infy.wookiee.kafka.TestHelper
import com.oracle.infy.wookiee.kafka.testing.model._
import com.oracle.infy.wookiee.kafka.WookieeKafka.getFreePort

import scala.jdk.CollectionConverters._

class KafkaTestHelperSpec extends TestHelper {
  "Kafka test helper" should {
    "read off a seeded topic" in {
      val topic = getFreshTopicName
      seedKafkaTopic(
        List(
          ("key1".getBytes, "value1".getBytes),
          ("key2".getBytes, "value2".getBytes),
          ("key3".getBytes, "value3".getBytes)
        ).asJava,
        topic
      )

      val records = getNEvents(3, topic)
      records.size mustEqual 3

      val javaRecords = getNJavaEvents(3, topic)
      javaRecords.size mustEqual 3
    }

    "get seeded consumer" in {
      val topic = getFreshTopicName
      val consumer = getSeededKafkaConsumer(
        List(
          ("key1".getBytes, "value1".getBytes),
          ("key2".getBytes, "value2".getBytes),
          ("key3".getBytes, "value3".getBytes)
        ).asJava,
        topic
      )

      // Could be more than 3 events due to other tests
      val records = getNEvents(3, topic, consumer, 15000L)
      records.size >= 3 mustEqual true
    }

    "start clustered ZK" in {
      val ports = List.fill(3)(getFreePort)
      val cluster = TestingClusterMode(ports.toArray)
      TestingClusterMode.unapply(cluster).get.getConnectString.split(",").length mustEqual 3
      cluster.getConnectString.split(",").length mustEqual 3
      cluster.close()

      val cluster2 = TestingClusterMode(3)
      cluster2.getConnectString.split(",").length mustEqual 3
      cluster2.close()

      val server = TestingServerMode()
      TestingServerMode.unapply(server).get mustEqual -1
      server.close()
    }

    "await method works as expected" in {
      intercept[RuntimeException] {
        awaitEvent({
          false
        }, 100L, true)
      }

      intercept[IllegalArgumentException] {
        awaitEvent({
          throw new IllegalArgumentException("test")
        }, 1000L, false)
      }
    }

    "use other seed signatures" in {
      val oneSeed = seedKafkaTopic(
        List(
          ("key1".getBytes, "value1".getBytes),
          ("key2".getBytes, "value2".getBytes),
          ("key3".getBytes, "value3".getBytes)
        ).asJava
      )
      getNEvents(3, oneSeed).size mustEqual 3

      val twoSeed = seedKafkaTopic(
        List(
          ("key1".getBytes, "value1".getBytes),
          ("key2".getBytes, "value2".getBytes),
          ("key3".getBytes, "value3".getBytes)
        ).asJava,
        "twoSeed"
      )
    }
  }
}
