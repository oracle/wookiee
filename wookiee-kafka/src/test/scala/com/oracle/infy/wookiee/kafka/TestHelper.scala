package com.oracle.infy.wookiee.kafka

import ch.qos.logback.classic.{Level, LoggerContext}
import com.oracle.infy.wookiee.kafka.testing.KafkaTestHelper
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.Try

trait TestHelper extends KafkaTestHelper with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val ec: ExecutionContext = execContext

  Try { // Set logger to INFO level
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    loggerContext.getLoggerList.forEach(logger => logger.setLevel(Level.INFO))
  }

  override protected def beforeAll(): Unit =
    startKafka()

  override protected def afterAll(): Unit =
    stopKafka()
}
