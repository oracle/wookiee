package com.oracle.infy.wookiee.model

import com.oracle.infy.wookiee.model.CaseInsensitiveMap.ciKey

import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._

case class CaseInsensitiveKey(key: String) {

  // Case-insensitive equality check
  override def equals(obj: Any): Boolean = obj match {
    case other: CaseInsensitiveKey => key.equalsIgnoreCase(other.key)
    case obj: String               => key.equalsIgnoreCase(obj)
    case _                         => false
  }

  // Case-insensitive hash code
  override def hashCode(): Int = key.toLowerCase.hashCode

  override def toString: String = key
}

trait CaseInsensitiveMapLike[A] extends Map[String, A] {
  protected val underlying: Map[CaseInsensitiveKey, A]
  protected val default: Option[A]

  override def get(key: String): Option[A] = underlying.get(ciKey(key))

  override def iterator: Iterator[(String, A)] = underlying.iterator.map { case (ciKey, value) => (ciKey.key, value) }

  override def contains(key: String): Boolean = underlying.contains(ciKey(key))

  override def size: Int = underlying.size

  override def foreach[U](f: ((String, A)) => U): Unit = underlying.foreach {
    case (ciKey, value) => f((ciKey.key, value))
  }

  override def apply(key: String): A = get(key) match {
    case None        => default(key)
    case Some(value) => value
  }

  override def default(key: String): A =
    default.getOrElse(underlying.default(ciKey(key)))

  override def applyOrElse[K1 <: String, V1 >: A](key: K1, default: K1 => V1): V1 =
    underlying.get(CaseInsensitiveKey(key)).map(value => value: V1).getOrElse(default(key))
}
