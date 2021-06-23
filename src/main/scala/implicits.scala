

import java.time.{Instant, ZoneId, ZonedDateTime}

import cats.implicits._
import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen._

import scala.util.Try



import cats.implicits._
import scala.util.Try

// NOTE: This code is generated. DO NOT EDIT!
object implicits {

  private def toZonedDateTime(l: Long): Either[GrpcConversionError, ZonedDateTime] = {
    Try {
      ZonedDateTime.ofInstant(Instant.ofEpochSecond(l), ZoneId.of("UTC"))
    }.toEither
      .left.map(t => GrpcConversionError(t.getMessage))
  }

  private def zonedDateTimeToLong(zdt: ZonedDateTime): Long = {
    zdt.toEpochSecond
  }
  implicit class RequestWithOptionToGrpc(lhs: RequestWithOption) {
    def toGrpc: GrpcRequestWithOption = {
      GrpcRequestWithOption(
        newProp = lhs.newProp.toGrpc,
        field = lhs.field.toGrpc
      )
    }
  }

  implicit class RequestWithOptionToADR(lhs: GrpcRequestWithOption) {
    def toADR: Either[GrpcConversionError, RequestWithOption] = {
      for {
        newProp <- lhs.newProp.map(_.toADR).get
        field <- lhs.field.map(_.toADR).get
} yield RequestWithOption(newProp = newProp,field = field)
    }
  }
  implicit class SomeResponseToGrpc(lhs: SomeResponse) {
    def toGrpc: GrpcSomeResponse = {
      lhs match {
        case a: FailureResponse => a.toGrpc
        case b: SuccessfulResponse => b.toGrpc
      }
    }
  }
  implicit class FailureResponseToGrpc(lhs: FailureResponse) {
    def toGrpc: GrpcFailureResponse = {
      GrpcFailureResponse(
        err = lhs.err
      )
    }
  }
  implicit class SuccessfulResponseToGrpc(lhs: SuccessfulResponse) {
    def toGrpc: GrpcSuccessfulResponse = {
      GrpcSuccessfulResponse(

      )
    }
  }

  implicit class SomeResponseToADR(lhs: GrpcSomeResponse) {
    def toADR: Either[GrpcConversionError, SomeResponse] = {
None        .orElse(lhs.asMessage.sealedValue.a.map(_.toADR))
        .orElse(lhs.asMessage.sealedValue.b.map(_.toADR)).getOrElse(Left(GrpcConversionError("Invalid sealed values")))
    }
  }
  implicit class FailureResponseToADR(lhs: GrpcFailureResponse) {
    def toADR: Either[GrpcConversionError, FailureResponse] = {
      for {
        err <- Right(lhs.err)
} yield FailureResponse(err = err)
    }
  }
  implicit class SuccessfulResponseToADR(lhs: GrpcSuccessfulResponse) {
    def toADR: Either[GrpcConversionError, SuccessfulResponse] = {
Right(SuccessfulResponse())
    }
  }
  implicit class OptionStringToGrpc(lhs: Option[String]) {
    def toGrpc: Option[GrpcOptionString] = {
lhs.map(v => GrpcSomeString(v))
    }
  }

  implicit class OptionStringToADR(lhs: GrpcOptionString) {
    def toADR: Either[GrpcConversionError, Option[String]] = {
None        .orElse(lhs.NoneString.a.map(_.toADR))
        .orElse(lhs.SomeString.b.map(_.toADR)).getOrElse(Left(GrpcConversionError("Invalid sealed values")))
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
  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
    def toGrpc: Option[GrpcOptionOptionString] = {
lhs.map(v => GrpcSomeOptionString(v.toGrpc))
    }
  }

  implicit class OptionOptionStringToADR(lhs: GrpcOptionOptionString) {
    def toADR: Either[GrpcConversionError, Option[Option[String]]] = {
None        .orElse(lhs.NoneOptionString.a.map(_.toADR))
        .orElse(lhs.OptionString.b.map(_.toADR)).getOrElse(Left(GrpcConversionError("Invalid sealed values")))
    }
  }
  implicit class NoneOptionStringToADR(lhs: GrpcNoneOptionString) {
    def toADR: Either[GrpcConversionError, Option[Option[String]]] = {
val _ = lhs
Right(None)
    }
  }
  implicit class OptionStringToADR(lhs: GrpcOptionString) {
    def toADR: Either[GrpcConversionError, Option[String]] = {
None        .orElse(lhs.NoneString.a.map(_.toADR))
        .orElse(lhs.SomeString.b.map(_.toADR)).getOrElse(Left(GrpcConversionError("Invalid sealed values")))
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
}