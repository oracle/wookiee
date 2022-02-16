package com.oracle.infy.wookiee.grpc.utils

import cats.Monad
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._

import java.io.{ByteArrayOutputStream, PrintWriter}
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec

object implicits {

  implicit class MultiversalEquality[T](left: T) {
    def ===(right: T): Boolean = left == right //scalafix:ok

    def /==(right: T): Boolean = left != right //scalafix:ok

    def =/=(right: T): Boolean = left /== right
  }

  implicit class ThrowableHelpers(t: Throwable) {

    def stackTrace: String = {
      val baos = new ByteArrayOutputStream()
      val pw = new PrintWriter(baos)
      t.printStackTrace(pw)
      pw.flush()
      baos.toString
    }
  }

  implicit class ToEitherT[A, F[_]: Monad: Sync](lhs: F[A]) {

    def toEitherT[B](handler: Throwable => B): EitherT[F, B, A] =
      EitherT(
        lhs
          .map(_.asRight[B])
          .handleErrorWith(t => handler(t).asLeft[A].pure[F])
      )
  }

  private def toBuf[T](itr: util.Iterator[T]): scala.collection.mutable.Buffer[T] = {
    val buf = scala.collection.mutable.Buffer[T]()

    @tailrec
    def add(): Unit =
      if (itr.hasNext) {
        buf += itr.next()
        add()
      } else {
        ()
      }

    add()
    buf
  }

  implicit class Java2ScalaConverterList[T](lhs: java.util.List[T]) {

    def asScala: List[T] =
      toBuf(lhs.iterator()).toList
  }

  implicit class Scala2JavaConverterList[T](lhs: Seq[T]) {

    def asJava: java.util.List[T] = {
      val list = new util.ArrayList[T]()
      lhs.foreach(list.add)
      list
    }
  }

  implicit class Scala2JavaConverterConcurrentHashMap[V](lhs: ConcurrentHashMap[_, V]) {

    def valueSet: Set[V] =
      toBuf(lhs.values().iterator()).toSet
  }
}
