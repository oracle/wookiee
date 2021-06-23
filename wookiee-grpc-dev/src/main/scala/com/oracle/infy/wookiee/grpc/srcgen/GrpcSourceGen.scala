package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import java.nio.file.{Files, Paths}
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

//    implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
//
//      def toGrpc: GrpcOptionOptionString = {
//        lhs match {
//          case None          => GrpcNoneNoneString()
//          case Some(Some(v)) => GrpcSomeSomeString(v) // Note: Call `v.toGrpc` for custom types
//          case Some(None)    => GrpcSomeNoneString()
//        }
//      }
//    }
//
//    implicit class OptionOptionStringToAdr(lhs: GrpcOptionOptionString) {
//      def toAdr: Either[GrpcConversionError, Option[Option[String]]] = {
//        None
//          .orElse(lhs.asMessage.sealedValue.a.map(_.toADR))
//          .orElse(lhs.asMessage.sealedValue.b.map(_.toADR))
//          .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
//      }
//    }
//
//    implicit class NoneNoneStringToADR(lhs: GrpcNoneNoneString) {
//      def toADR: Either[GrpcConversionError, Option[Option[String]]] = {
//        val _ = lhs
//        Right(None)
//      }
//    }
//
//    implicit class SomeSomeStringToADR(lhs: GrpcSomeSomeString) {
//      def toADR: Either[GrpcConversionError, Option[Option[String]]] = {
//        Right(Some(Some(lhs.value))) // Note: Should call `lhs.toADR` if its a custom type
//      }
//    }
//
//    implicit class SomeNoneStringToADR(lhs: GrpcSomeNoneString) {
//      def toADR: Either[GrpcConversionError, Option[Option[String]]] = {
//        val _ = lhs
//        Right(Some(None))
//      }
//    }

    val protoFilePath = "/Users/drreynol/source/wookiee/wookiee-proto/src/main/protobuf/someService2.proto"
    val scalaFilePath = "/Users/drreynol/source/wookiee/src/main/scala/implicits.scala"

    Files.write(Paths.get(protoFilePath), protoSrc.getBytes)
    Files.write(Paths.get(scalaFilePath), scalaSrc.getBytes)
    ()

  }
}
