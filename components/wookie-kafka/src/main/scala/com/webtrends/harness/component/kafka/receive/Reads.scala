package com.webtrends.harness.component.kafka.receive

import java.nio.charset.StandardCharsets

import scala.annotation.implicitNotFound


@implicitNotFound("No Reads found for type ${A}. Try to implement Reads for type ${A}")
trait Reads[A] {
  /**
   * Takes a Array[Byte] and reads an A
   * @param rawMessage
   * @return
   */
  def read(rawMessage: Array[Byte]): A
}

/**
 * Default conversions
 */
object Reads {
  val utf8 = StandardCharsets.UTF_8

  /**
   * Default, just passes back the same Array[Byte]
   *
   */
  implicit object ByteReads extends Reads[Array[Byte]] {

    override def read(rawMessage: Array[Byte]): Array[Byte] = rawMessage
  }

  /**
   * Converts an encoded utf8 Array[Byte] to a String
   */
  implicit object StringUtf8Reads extends Reads[String] {

    override def read(rawMessage: Array[Byte]): String = new String(rawMessage, utf8)
  }

}