package com.oracle.infy.wookiee.kafka.consume

import com.oracle.infy.wookiee.kafka.KafkaTestHelper
import com.oracle.infy.wookiee.kafka.WookieeKafka.{WookieeRecord, createTopic}
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import com.oracle.infy.wookiee.utils.ThreadUtil

class WookieeKafkaConsumerSpec extends KafkaTestHelper {
  "WookieeKafkaConsumer" should {
    "be able to subscribe in various ways" in {
      val consumer = WookieeKafkaConsumer("localhost:9092", "test-group")
      consumer.assign(Seq(("test-topic", 0), ("test-topic", 1)))
      consumer.assignment() mustEqual Set(("test-topic", 0), ("test-topic", 1))
      consumer.unsubscribe()

      consumer.subscribe("test-topic2")
      consumer.subscription() mustEqual Set("test-topic2")
      consumer.unsubscribe()

      consumer.subscribe("test-topic.*".r.pattern)

      consumer.underlying.close()
    }

    "honor commits" in {
      createTopic(adminClient, "commit-topic")
      val producer = WookieeKafkaProducer(s"localhost:$kafkaPort")

      val consumer =
        WookieeKafkaConsumer(s"localhost:$kafkaPort", "test-group", enableAutoCommit = false, resetToLatest = false)
      def pollForRecords(expectedKey: String, commitFunc: WookieeKafkaConsumer => Unit): Unit = {
        consumer.subscribe("commit-topic")
        ThreadUtil.awaitEvent({
          val records = consumer.poll(1000L)
          records.size mustEqual 1
          records.head.getKey == expectedKey
        })
        commitFunc(consumer)
        consumer.unsubscribe()
      }

      // First message and commit
      producer.send("commit-topic", WookieeRecord("key1", "value1"))
      pollForRecords("key1", _.commitSync())
      // Second message and commit
      producer.send("commit-topic", WookieeRecord("key2", "value2"))
      pollForRecords("key2", _.commitSync(Map(("commit-topic", 0) -> 2L)))
      // Third message and commit
      producer.send("commit-topic", WookieeRecord("key3", "value3"))
      pollForRecords("key3", _.commitAsync())
      // Fourth and final message
      producer.send("commit-topic", WookieeRecord("key4", "value4"))
      pollForRecords("key4", _.commitAsync())
      consumer.partitionsFor("commit-topic").size mustEqual 1
      consumer.close()
    }

    "be able to seek and deal with offsets" in {
      createTopic(adminClient, "offset-topic")
      val producer = WookieeKafkaProducer(s"localhost:$kafkaPort")

      val consumer =
        WookieeKafkaConsumer(s"localhost:$kafkaPort", "test-group", enableAutoCommit = false, resetToLatest = false)
      0.until(5).foreach { i =>
        producer.send("offset-topic", WookieeRecord(s"key$i", s"value$i"))
      }
      consumer.assign(Seq("offset-topic" -> 0))
      consumer.listTopics().head mustEqual "offset-topic"
      consumer.position(("offset-topic", 0)) mustEqual 0L
      consumer.seekToEnd(Seq("offset-topic" -> 0))
      consumer.position(("offset-topic", 0)) mustEqual 5L
      consumer.seekToBeginning(Seq("offset-topic" -> 0))
      consumer.position(("offset-topic", 0)) mustEqual 0L
      consumer.seek(("offset-topic", 0), 2L)
      consumer.position(("offset-topic", 0)) mustEqual 2L

      consumer.beginningOffsets(Seq("offset-topic" -> 0)).head._2 mustEqual 0L
      consumer.endOffsets(Seq("offset-topic" -> 0)).head._2 mustEqual 5L
    }
  }
}
