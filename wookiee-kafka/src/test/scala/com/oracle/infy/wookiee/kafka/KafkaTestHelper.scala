package com.oracle.infy.wookiee.kafka

import com.oracle.infy.wookiee.kafka.WookieeKafka.WookieeRecord
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.apache.curator.test.TestingServer
import org.apache.kafka.clients.admin.AdminClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

trait KafkaTestHelper extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with LoggingAdapter {
  lazy val zkServer = new TestingServer()
  lazy val (kafkaPort, kafkaServer) = WookieeKafka.startLocalKafkaServer(zkServer.getConnectString)
  lazy val adminClient: AdminClient = WookieeKafka.createAdminClient(s"localhost:$kafkaPort")
  implicit val ec: ExecutionContext = ThreadUtil.createEC(s"wookiee-kafka-${getClass.getSimpleName}")

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
    log.info(s"Starting Kafka tests on port [$kafkaPort]")
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    log.info(s"Stopping Kafka tests on port [$kafkaPort]")
    kafkaServer.close()
  }
}
