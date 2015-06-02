package com.webtrends.harness.component.kafka.receive

import com.webtrends.harness.component.kafka.util.KafkaMessageSet


/**
 *
 * @param msgs
 * @param nextOffsetOfSet next offset after this set of messages
 */
case class MessageResponse(msgs: KafkaMessageSet, nextOffsetOfSet: Long) {

  /**
   * Return the messages transformed, according to the Reads in scope
   * @param reads
   * @tparam A
   * @return
   */
  def messages[A](implicit reads: Reads[A]): Iterable[MessageOffset[A]] = {
    for {
      msg <- msgs
    } yield {
      val payload = msg.message.payload
      val bytes = new Array[Byte](payload.limit())
      payload.get(bytes)
      MessageOffset(reads.read(bytes), msg.offset, msg.nextOffset)
    }
  }

  val totalBytes : Int = msgs.ms.sizeInBytes

  lazy val numberOfMsgs = msgs.size

}

/**
 *
 * @param message
 * @param offset
 * @param nextOffset
 * @tparam A
 */
case class MessageOffset[A] (message: A, offset: Long, nextOffset: Long)


