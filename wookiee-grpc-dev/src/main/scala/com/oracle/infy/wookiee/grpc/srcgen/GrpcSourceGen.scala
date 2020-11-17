package com.oracle.infy.wookiee.grpc.srcgen

import java.nio.file.{Files, Paths}

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import scala.reflect.runtime.universe.typeOf

object GrpcSourceGen extends SrcGen {

  final case class FooRequest(field: String)

  sealed trait SomeResponse
  final case class SuccessfulResponse() extends SomeResponse
  final case class FailureResponse(err: String) extends SomeResponse

  final case class RequestWithOption(
      field: Option[FooRequest],
      listField: Option[List[String]]
  )

  def main(args: Array[String]): Unit = {

    val types = List(
      typeOf[FooRequest],
      typeOf[SomeResponse],
      typeOf[RequestWithOption]
    ).map(_.typeSymbol)

    val sealedTypeLookup = sealedTypes(types)
    val records = types.map(toRecord)

    val rpcs = List(
      typeOf[IO[FooRequest, SomeResponse]] -> "someRpc"
    )
    val protoSrc = genService(rpcs, records, sealedTypeLookup, "some.package", "SomeService")

    val scalaSrc = genScala(
      records,
      sealedTypeLookup,
      """
        |import java.time.{Instant, ZoneId, ZonedDateTime}
        |
        |import cats.implicits._
        |import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen._
        |import some.`package`.deleteMe._
        |
        |import scala.util.Try
        |
        |""".stripMargin
    )

    // Write these to a file
    println(protoSrc)
    println(scalaSrc)
    ()

  }
}
