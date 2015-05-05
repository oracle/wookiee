package com.webtrends.harness.component.kafka

import java.nio.charset.StandardCharsets

import com.webtrends.harness.component.kafka.receive.{MessageOffset, MessageResponse}
import com.webtrends.harness.component.kafka.util.KafkaMessageSet
import kafka.message.{MessageSet, ByteBufferMessageSet, Message}
import org.junit.runner.RunWith
import org.slf4j.{LoggerFactory, Logger}
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions


@RunWith(classOf[JUnitRunner])
class MessageResponseSpec extends SpecificationLike with NoTimeConversions {
  val utf8 = StandardCharsets.UTF_8
  protected final val log:Logger = LoggerFactory.getLogger(getClass)

  "Message Response " should {
    "be able to read a Array[Byte]" in {
      val value = "Testing".getBytes(utf8)
      val message = new Message(value)
      val messageSet:MessageSet = new ByteBufferMessageSet(message)
      val kafkaMessageSet = KafkaMessageSet(messageSet, 0)

      val response = MessageResponse(kafkaMessageSet, 1)

      val readMessages = response.messages[Array[Byte]]


      readMessages must have size(1)

      readMessages.head.message.mkString must beEqualTo(value.mkString)
    }



    "be able to read a String" in {
      val value = "YO"
      val message = new Message(value.getBytes(utf8))
      val messageSet:MessageSet = new ByteBufferMessageSet(message)
      val kafkaMessageSet = KafkaMessageSet(messageSet, 0)

      val response = MessageResponse(kafkaMessageSet, 1)

      val readMessages = response.messages[String]


      readMessages must have size(1)
      readMessages.head.message must beEqualTo(value)
    }


  }
}
