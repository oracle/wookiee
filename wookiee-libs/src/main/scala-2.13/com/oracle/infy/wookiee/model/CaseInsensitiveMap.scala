package com.oracle.infy.wookiee.model

import com.oracle.infy.wookiee.model.CaseInsensitiveMap.ciKey

import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._

// This Map implementation is case insensitive for keys and can contain anything
// It is thread safe and immutable (so save it to the same (or a new) variable after each update)
// Note that calling any mapping function turns this into a normal Map, and all keys will be lower case
// The exception is the map function, which also allows you to specify a new default value
class CaseInsensitiveMap[A] private (
    protected override val underlying: Map[CaseInsensitiveKey, A],
    protected override val default: Option[A]
) extends CaseInsensitiveMapLike[A] {
  override def get(key: String): Option[A] = underlying.get(ciKey(key))

  override def iterator: Iterator[(String, A)] = underlying.iterator.map { case (ciKey, value) => (ciKey.key, value) }

  override def +[B1 >: A](kv: (String, B1)): CaseInsensitiveMap[B1] =
    new CaseInsensitiveMap(underlying + (ciKey(kv._1) -> kv._2), default)

  def --(keys: Iterable[String]): CaseInsensitiveMap[A] = {
    val keysToRemove = keys.map(ciKey).toSet
    new CaseInsensitiveMap(underlying.filter {
      case (key, _) =>
        !keysToRemove.contains(key)
    }, default)
  }

  def removed(key: String): CaseInsensitiveMap[A] =
    new CaseInsensitiveMap[A](underlying - ciKey(key), default)

  def removedAll(keys: Iterable[String]): CaseInsensitiveMap[A] =
    this -- keys

  override def contains(key: String): Boolean = underlying.contains(ciKey(key))

  override def size: Int = underlying.size

  override def updated[V1 >: A](key: String, value: V1): CaseInsensitiveMap[V1] =
    new CaseInsensitiveMap(underlying.updated(ciKey(key), value), default)

  override def empty: CaseInsensitiveMap[A] = new CaseInsensitiveMap(Map.empty, default)

  // This method is the only safe mapping that will preserve case insensitivity
  def map[V2](f: ((String, A)) => (String, V2), newDefault: Option[V2]): CaseInsensitiveMap[V2] = {
    val newEntries = underlying.map {
      case (key, value) =>
        val (newKey, newValue) = f((key.key, value))
        ciKey(newKey) -> newValue
    }
    new CaseInsensitiveMap(newEntries, newDefault)
  }

  override def foreach[U](f: ((String, A)) => U): Unit = underlying.foreach {
    case (ciKey, value) => f((ciKey.key, value))
  }

  override def +[V1 >: A](elem1: (String, V1), elem2: (String, V1), elems: (String, V1)*): CaseInsensitiveMap[V1] = {
    val combinedEntries = underlying.map { case (ciKey, value) => (ciKey.key, value: V1) } + elem1 + elem2 ++ elems
    CaseInsensitiveMap(combinedEntries, default)
  }

  // Compatible with both Scala 2.12 and 2.13
  def ++[V1 >: A](xs: Iterable[(String, V1)]): CaseInsensitiveMap[V1] = {
    val combinedEntries = underlying.map { case (ciKey, value) => (ciKey.key, value: V1) } ++ xs
    CaseInsensitiveMap(combinedEntries, default)
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

object CaseInsensitiveMap {

  def apply[A](map: Map[String, A], default: Option[A]): CaseInsensitiveMap[A] = {
    val normalizedMap = map.map { case (k, v) => ciKey(k) -> v }
    new CaseInsensitiveMap[A](normalizedMap, default)
  }

  def apply[A](map: Map[String, A]): CaseInsensitiveMap[A] =
    CaseInsensitiveMap[A](map, None)

  def empty[A]: CaseInsensitiveMap[A] =
    CaseInsensitiveMap.empty[A](None)

  def empty[A](default: Option[A]): CaseInsensitiveMap[A] =
    CaseInsensitiveMap[A](Map.empty[String, A], default)

  def apply[A](entries: (String, A)*): CaseInsensitiveMap[A] =
    CaseInsensitiveMap[A](entries.toMap)

  def apply[A](javaMap: JavaMap[String, A]): CaseInsensitiveMap[A] =
    CaseInsensitiveMap[A](javaMap.asScala.toMap, None)

  def apply[A](javaMap: JavaMap[String, A], default: A): CaseInsensitiveMap[A] =
    CaseInsensitiveMap[A](javaMap.asScala.toMap, Some(default))

  // Helper to create a CaseInsensitiveKey
  protected[oracle] def ciKey(key: String): CaseInsensitiveKey = CaseInsensitiveKey(key)
}
