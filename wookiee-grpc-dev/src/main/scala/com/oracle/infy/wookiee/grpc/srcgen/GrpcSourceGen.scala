package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import java.nio.file.{Files, Paths}
import scala.reflect.runtime.universe.typeOf

object GrpcSourceGen extends SrcGen {

  final case class FooRequest(field: String)

  sealed trait SomeResponse
  final case class SuccessfulResponse() extends SomeResponse
  final case class FailureResponse(err: String) extends SomeResponse

  final case class RequestWithOption(
      field: Option[Option[String]],
      newProp: Option[String]
  )

  def main(args: Array[String]): Unit = {

    val types = List(
      typeOf[RequestWithOption],
      typeOf[SomeResponse]
    ).map(_.typeSymbol)

    val sealedTypeLookup = sealedTypes(types)
    val records = types.map(toRecord)

    val rpcs = List(
      typeOf[IO[RequestWithOption, SomeResponse]] -> "someRpc"
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
        |
        |import scala.util.Try
        |
        |""".stripMargin
    )

    // Write these to a file

//    val x: Option[String] = ???
//    val y: Option[String] = ???
//
//    val z: Option[Option[String]] = ???
//
//
//    (x, y) match {
//      case (None, None) =>
//      case (Some(_), Some(_)) =>
//      case (None, Some(_)) =>
//      case (Some(_), None) =>
//    }
//
//    z match {
//      case Some(None) =>
//      case Some(Some(_)) =>
//      case None =>
//    }

    val protoFilePath = "/Users/lachandr/Projects/wookiee/wookiee-proto/src/main/protobuf/someService.proto"
    val scalaFilePath = "/Users/lachandr/Projects/wookiee/src/main/scala/implicits.scala"

    Files.write(Paths.get(protoFilePath), protoSrc.getBytes)
    Files.write(Paths.get(scalaFilePath), scalaSrc.getBytes)
    ()

  }
}
