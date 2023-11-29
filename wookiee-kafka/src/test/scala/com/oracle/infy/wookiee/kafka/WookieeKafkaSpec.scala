package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.KafkaObjects.{AutoCloseableConsumer, MessageData, WookieeOffset, WookieeRecord}
import com.oracle.infy.wookiee.kafka.WookieeKafka._
import com.oracle.infy.wookiee.kafka.consume.WookieeKafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats}

import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import scala.util.Try

class WookieeKafkaSpec extends TestHelper {
  override def autoCreateTopics: Boolean = false
  case class ValueHolder(value: String)

  "WookieeKafka" should {
    "be able to create a topic" in {
      val (compacted, parts, repl) = createTopic(adminClient, "test-topic")
      compacted mustEqual false
      parts mustEqual 1
      repl mustEqual 1
      val (compacted2, parts2, repl2) = createTopic(adminClient, "test-topic2", compacted = true, Some(1), Some(1))
      compacted2 mustEqual true
      parts2 mustEqual 1
      repl2 mustEqual 1
      adminClient.listTopics().names().get().asScala.contains("test-topic") mustEqual true
    }

    "start local kafka" in {
      val localKafka = startLocalKafkaServer(zkMode.getConnectString)
      localKafka._2.close()
      val localKafka2 = startLocalKafkaServer(zkMode.getConnectString, None)
      localKafka2._2.close()
    }

    "produce and consume a basic message" in {
      createTopic(adminClient, "basic-topic")
      @volatile var receivedKey: String = ""
      @volatile var receivedVal: String = ""
      val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort")
      val consumer = WookieeKafka.startConsumerAndProcess(
        s"localhost:$kafkaPort",
        "basic-group",
        Seq("basic-topic"),
        msg => {
          receivedKey = msg.getKey
          receivedVal = msg.getValue
          ()
        }
      )

      awaitEvent({
        producer.send("basic-topic", Some("key"), "value")
        Thread.sleep(1000L)
        receivedKey == "key" && receivedVal == "value"
      })

      consumer.close()
      producer.close()
    }

    "produce and consume a pattern topic" in {
      createTopic(adminClient, "pattern-topic")
      @volatile var receivedKey: String = ""
      @volatile var receivedVal: String = ""
      val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort")
      val consumer = WookieeKafka.startConsumerAndProcessPattern(
        s"localhost:$kafkaPort",
        "basic-group",
        "pattern-.*".r.pattern,
        msg => {
          receivedKey = msg.getKey
          receivedVal = msg.getValue
          ()
        }
      )

      awaitEvent({
        producer.send("pattern-topic", "key", "value")
        Thread.sleep(1000L)
        receivedKey == "key" && receivedVal == "value"
      })

      consumer.close()
      producer.close()
    }

    "produce onto a certain partition" in {
      createTopic(adminClient, "partition-topic", compacted = false, partitions = Some(3))
      val expectedPartition = 2
      @volatile var rightPartition: Boolean = false
      val (producer, consumer) = getProducerAndConsumer(
        "basic-group",
        "partition-topic",
        msg => {
          rightPartition = msg.partition.get === expectedPartition
          ()
        }
      )

      producer.send("partition-topic", Some("key".getBytes), "value".getBytes, Some(2))

      awaitEvent({
        rightPartition
      })

      consumer.close()
      producer.close()
    }

    "fail gracefully on message processing" in {
      createTopic(adminClient, "fail-topic")
      @volatile var receivedKey: String = ""
      @volatile var receivedVal: String = ""
      val (producer, consumer) = getProducerAndConsumer(
        "basic-group",
        "fail-topic",
        msg => {
          if (msg.getKey == "outer-fail")
            throw new RuntimeException("outer-fail")

          receivedKey = msg.getKey
          receivedVal = msg.getValue
          ()
        }
      )

      producer.send("fail-topic", Some("outer-fail"), "value")
      producer.send("fail-topic", Some("key"), "value")

      awaitEvent({
        receivedKey == "key" && receivedVal == "value"
      })

      consumer.close()
      producer.close()
    }

    "fail gracefully on polling failure" in {
      @volatile var errorHit = false
      var autoCloseableConsumer: AutoCloseableConsumer = null
      def closeConsumer(): Unit = autoCloseableConsumer.close()

      val specialConsumer = new WookieeKafkaConsumer(
        s"localhost:$kafkaPort",
        "basic-group",
        enableAutoCommit = true,
        resetToLatest = false
      ) {
        override def poll(durationMillis: WookieeOffset): Seq[WookieeRecord] = {
          closeConsumer()
          throw new RuntimeException("polling failure")
        }
      }

      autoCloseableConsumer = new AutoCloseableConsumer(
        specialConsumer,
        _ => (),
        1000L
      ) {
        override def logPollingError(ex: Throwable): Unit = {
          errorHit = true
          super.logPollingError(ex)
        }
      }

      awaitEvent({
        errorHit
      })
      awaitEvent({
        Try(specialConsumer.close())
        !autoCloseableConsumer.shouldRun
      })
    }

    "consume a number of messages" in {
      createTopic(adminClient, "volume-topic")
      val toSend = 100
      val messagesSeen: AtomicInteger = new AtomicInteger(0)
      val (producer, consumer) = getProducerAndConsumer("basic-group", "volume-topic", msg => {
        messagesSeen.incrementAndGet()
        msg.commitOffsets()
        ()
      })

      0.until(100).foreach { _ =>
        producer.send("volume-topic", "value")
      }

      awaitEvent({
        messagesSeen.get() == toSend
      })
      Thread.sleep(1000L)
      consumer.close()
      producer.close()
    }

    "consume manually" in {
      createTopic(adminClient, "manual-topic", compacted = true)
      val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort")
      val consumer = WookieeKafka.startConsumer(
        s"localhost:$kafkaPort",
        "basic-group"
      )
      consumer.subscribe("manual-topic")
      // Create initial checkpoints
      consumer.poll(1000L)

      awaitEvent({
        producer.send("manual-topic", Some("key"), "value")
        val messages = consumer.poll(1000L)
        messages.nonEmpty && messages.head.getKey == "key" && messages.head.getValue == "value"
      })
      consumer.close()
      consumer.isClosed() mustEqual true
      producer.close()
    }

    "produce with a callback" in {
      val customProps = new java.util.Properties()
      customProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1000")
      val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort", customProps)
      @volatile var exHit = false
      producer.send("callback-topic", Some("key"), "value", {
        case Left(_)  => exHit = true
        case Right(_) => ()
      }: Either[Exception, MessageData] => Unit)
      awaitEvent({
        exHit
      })
      @volatile var goodHit = false
      createTopic(adminClient, "callback-topic", compacted = false, partitions = Some(3))
      producer.send("callback-topic", Some("key"), "value", {
        case Left(_)  => ()
        case Right(_) => goodHit = true
      }: Either[Exception, MessageData] => Unit)
      awaitEvent({
        goodHit
      })
      goodHit = false
      producer.send(
        "callback-topic",
        WookieeRecord("key".getBytes, "value".getBytes, Some(2)), {
          case Left(_)    => ()
          case Right(msg) => goodHit = msg.partition === 2
        }: Either[Exception, MessageData] => Unit
      )
      awaitEvent({
        goodHit
      })
      producer.underlying.close()
    }

    "have a robust record class" in {
      implicit val formats: Formats = DefaultFormats
      val key = """{"key":"key"}"""
      val value = """{"value":"value"}"""
      val kafkaMessage = WookieeRecord(key, value)
      kafkaMessage.getKey mustEqual key
      kafkaMessage.getValue mustEqual value
      val expected: (Array[Byte], Array[Byte]) = (key.getBytes, value.getBytes)
      val actual = WookieeRecord.unapply(kafkaMessage).get
      kafkaMessage.commitOffsets()
      actual._1.sameElements(expected._1) mustEqual true
      actual._2.sameElements(expected._2) mustEqual true
      Serialization.write(kafkaMessage.jsonKey) mustEqual key
      Serialization.write(kafkaMessage.jsonValue) mustEqual value

      val messageData = MessageData(key, value, 0L, 0, "topic", 0L)
      MessageData.unapply(messageData).get._1 mustEqual key
    }
  }
}
