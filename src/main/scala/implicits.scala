import com.oracle.infy.test.someService2._
import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen._

import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.util.Try

// NOTE: This code is generated. DO NOT EDIT!
object implicits {

  private def toZonedDateTime(l: Long): Either[GrpcConversionError, ZonedDateTime] = {
    Try {
      ZonedDateTime.ofInstant(Instant.ofEpochSecond(l), ZoneId.of("UTC"))
    }.toEither
      .left
      .map(t => GrpcConversionError(t.getMessage))
  }

  private def zonedDateTimeToLong(zdt: ZonedDateTime): Long = {
    zdt.toEpochSecond
  }

  implicit class ResponseToGrpc(lhs: Response) {

    def toGrpc: GrpcResponse = {
      GrpcResponse(
        field = lhs.field
      )
    }
  }

  implicit class ResponseToADR(lhs: GrpcResponse) {

    def toADR: Either[GrpcConversionError, Response] = {
      for {
        field <- Right(lhs.field)
      } yield Response(field = field)
    }
  }

  implicit class RequestToGrpc(lhs: Request) {

    def toGrpc: GrpcRequest = {
      GrpcRequest(
        field = lhs.field.toGrpc
      )
    }
  }

  implicit class RequestToADR(lhs: GrpcRequest) {

    def toADR: Either[GrpcConversionError, Request] = {
      for {
        field <- lhs.field.toADR
      } yield Request(field = field)
    }
  }

  implicit class OptionStringToGrpc(lhs: Option[String]) {

    def toGrpc: GrpcOptionString = {
      lhs match {
        case None    => GrpcNoneString()
        case Some(v) => GrpcSomeString(v)
      }
    }
  }

  implicit class OptionStringToADR(lhs: GrpcOptionString) {

    def toADR: Either[GrpcConversionError, Option[String]] = {
      None
        .orElse(lhs.asMessage.sealedValue.a.map(_.toADR))
        .orElse(lhs.asMessage.sealedValue.b.map(_.toADR))
        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
    }
  }

  implicit class NoneStringToADR(lhs: GrpcNoneString) {

    def toADR: Either[GrpcConversionError, Option[String]] = {
      val _ = lhs
      Right(None)
    }
  }

  implicit class SomeStringToADR(lhs: GrpcSomeString) {

    def toADR: Either[GrpcConversionError, Option[String]] = {
      for {
        value <- Right(lhs.value)
      } yield Option(value)
    }
  }

  implicit class OptionOptionRequestToGrpc(lhs: Option[Option[Request]]) {

    def toGrpc: GrpcOptionOptionRequest = {
      lhs match {
        case None          => GrpcNoneNoneRequest()
        case Some(Some(v)) => GrpcSomeSomeRequest(Some(v.toGrpc))
        case Some(None)    => GrpcSomeNoneRequest()
      }
    }
  }

  implicit class OptionOptionRequestToADR(lhs: GrpcOptionOptionRequest) {

    def toADR: Either[GrpcConversionError, Option[Option[Request]]] = {
      None
        .orElse(lhs.asMessage.sealedValue.a.map(_.toADR))
        .orElse(lhs.asMessage.sealedValue.b.map(_.toADR))
        .orElse(lhs.asMessage.sealedValue.c.map(_.toADR))
        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
    }
  }

  implicit class NoneNoneRequestToADR(lhs: GrpcNoneNoneRequest) {

    def toADR: Either[GrpcConversionError, Option[Option[Request]]] = {
      val _ = lhs
      Right(None)
    }
  }

  implicit class SomeSomeRequestToADR(lhs: GrpcSomeSomeRequest) {

    def toADR: Either[GrpcConversionError, Option[Option[Request]]] = {
      for {
        value <- lhs.getValue.toADR
      } yield Option(value)
    }
  }

  implicit class SomeNoneRequestToADR(lhs: GrpcSomeNoneRequest) {

    def toADR: Either[GrpcConversionError, Option[Option[Request]]] = {
      val _ = lhs
      Right(None)
    }
  }
}
