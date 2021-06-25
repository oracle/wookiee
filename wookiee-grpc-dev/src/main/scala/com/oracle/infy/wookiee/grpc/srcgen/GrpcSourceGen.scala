package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import scala.reflect.runtime.universe.typeOf

object GrpcSourceGen extends SrcGen {

  final case class Request(field: Option[String])

  final case class GrpcConversionError(msg: String)

  final case class Response(
      field: Option[Option[Request]]
  )

  def main(args: Array[String]): Unit = {

    val types = List(
      typeOf[Response],
      typeOf[Request]
    ).map(_.typeSymbol)

    val sealedTypeLookup = sealedTypes(types)
    val records = types.map(toRecord)

    val rpcs = List(
      typeOf[IO[Request, Response]] -> "method"
    )
    val protoSrc = genService(rpcs, records, sealedTypeLookup, "com.oracle.infy.test", "SomeService")

    val scalaSrc = genScala(
      records,
      sealedTypeLookup,
      """
        |import java.time.{Instant, ZoneId, ZonedDateTime}
        |
        |import cats.implicits._
        |import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen._
        |import com.oracle.infy.test.someService2._
        |import scala.util.Try
        |
        |""".stripMargin
    )

    println(protoSrc)
    println(scalaSrc)
    ()
  }
}
