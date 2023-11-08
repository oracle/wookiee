package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.KafkaObjects.WookieeRecord
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import org.apache.curator.test.TestingServer
import org.apache.kafka.clients.admin.AdminClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait KafkaTestHelper extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  lazy val zkServer = new TestingServer()
  lazy val (kafkaPort, kafkaServer) = WookieeKafka.startLocalKafkaServer(zkServer.getConnectString)
  lazy val adminClient: AdminClient = WookieeKafka.createAdminClient(s"localhost:$kafkaPort")

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool(Executors.defaultThreadFactory()))

  def getProducerAndConsumer(
      groupId: String,
      topic: String,
      process: WookieeRecord => Unit,
      resetToLatest: Boolean = true
  ): (WookieeKafkaProducer, AutoCloseable) = {
    val producer = WookieeKafka.startProducer(s"localhost:$kafkaPort")
    val consumer = WookieeKafka.startConsumerAndProcess(
      s"localhost:$kafkaPort",
      groupId,
      Seq(topic),
      process,
      resetToLatest = resetToLatest
    )
    (producer, consumer)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    KafkaObjects.log.info(s"Starting Kafka tests on port [$kafkaPort]")
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    KafkaObjects.log.info(s"Stopping Kafka tests on port [$kafkaPort]")
    kafkaServer.close()
  }

  // Copied from ThreadUtil to avoid dependency
  // A helper method to wait for a true evaluation, great for testing
  def awaitEvent(
      f: => Boolean,
      waitMs: Long = 15000L,
      ignoreError: Boolean = true
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
