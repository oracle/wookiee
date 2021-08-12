package com.oracle.infy.wookiee.srcgen
import Example._
import com.oracle.infy.wookiee.grpc.srcgen.testService.testService._

object implicits {

  implicit class ASErrorToGrpc(lhs: ASError) {

    def toGrpc: GrpcASError = lhs match {
      case value: DestinationError =>
        GrpcASError(GrpcASError.OneOf.DestinationError(value.toGrpc))
      case value: ConnectionError =>
        GrpcASError(GrpcASError.OneOf.ConnectionError(value.toGrpc))
      case _ =>
        GrpcASError(GrpcASError.OneOf.Empty)
    }
  }

  implicit class ASErrorFromGrpc(lhs: GrpcASError) {

    def fromGrpc: Either[String, ASError] = lhs.oneOf match {
      case GrpcASError.OneOf.Empty =>
        Left("err")
      case GrpcASError.OneOf.DestinationError(value) =>
        value.fromGrpc
      case GrpcASError.OneOf.ConnectionError(value) =>
        value.fromGrpc
    }
  }

  implicit class DestinationErrorToGrpc(lhs: DestinationError) {

    def toGrpc: GrpcDestinationError = lhs match {
      case value: MaxyDestinationValidationError =>
        GrpcDestinationError(GrpcDestinationError.OneOf.MaxyDestinationValidationError(value.toGrpc))
      case value: MaxyConnectionValidationError =>
        GrpcDestinationError(GrpcDestinationError.OneOf.MaxyConnectionValidationError(value.toGrpc))
      case _ =>
        GrpcDestinationError(GrpcDestinationError.OneOf.Empty)
    }
  }

  implicit class DestinationErrorFromGrpc(lhs: GrpcDestinationError) {

    def fromGrpc: Either[String, DestinationError] = lhs.oneOf match {
      case GrpcDestinationError.OneOf.Empty =>
        Left("err")
      case GrpcDestinationError.OneOf.MaxyDestinationValidationError(value) =>
        value.fromGrpc
      case GrpcDestinationError.OneOf.MaxyConnectionValidationError(value) =>
        value.fromGrpc
    }
  }

  implicit class ConnectionErrorToGrpc(lhs: ConnectionError) {

    def toGrpc: GrpcConnectionError = lhs match {
      case value: MaxyConnectionValidationError =>
        GrpcConnectionError(GrpcConnectionError.OneOf.MaxyConnectionValidationError(value.toGrpc))
      case _ =>
        GrpcConnectionError(GrpcConnectionError.OneOf.Empty)
    }
  }

  implicit class ConnectionErrorFromGrpc(lhs: GrpcConnectionError) {

    def fromGrpc: Either[String, ConnectionError] = lhs.oneOf match {
      case GrpcConnectionError.OneOf.Empty =>
        Left("err")
      case GrpcConnectionError.OneOf.MaxyConnectionValidationError(value) =>
        value.fromGrpc
    }
  }

  implicit class PersonToGrpc(lhs: Person) {

    def toGrpc: GrpcPerson =
      GrpcPerson(name = lhs.name, age = lhs.age)
  }

  implicit class PersonFromGrpc(lhs: GrpcPerson) {

    def fromGrpc: Either[String, Person] =
      for {
        name <- Right(lhs.name)
        age <- Right(lhs.age)
      } yield Person(name = name, age = age)
  }

  implicit class MaxyDestinationValidationErrorToGrpc(lhs: MaxyDestinationValidationError) {

    def toGrpc: GrpcMaxyDestinationValidationError =
      GrpcMaxyDestinationValidationError(
        code = lhs.code,
        maxyError = lhs.maxyError,
        person = Some(lhs.person.toGrpc),
        details = Some(lhs.details.toGrpc)
      )
  }

  implicit class MaxyDestinationValidationErrorFromGrpc(lhs: GrpcMaxyDestinationValidationError) {

    def fromGrpc: Either[String, MaxyDestinationValidationError] =
      for {
        code <- Right(lhs.code)
        maxyError <- Right(lhs.maxyError)
        person <- lhs.getPerson.fromGrpc
        details <- lhs.getDetails.fromGrpc
      } yield MaxyDestinationValidationError(code = code, maxyError = maxyError, person = person, details = details)
  }

  implicit class MaxyConnectionValidationErrorToGrpc(lhs: MaxyConnectionValidationError) {

    def toGrpc: GrpcMaxyConnectionValidationError =
      GrpcMaxyConnectionValidationError(code = lhs.code, maxyError = lhs.maxyError, person = Some(lhs.person.toGrpc))
  }

  implicit class MaxyConnectionValidationErrorFromGrpc(lhs: GrpcMaxyConnectionValidationError) {

    def fromGrpc: Either[String, MaxyConnectionValidationError] =
      for {
        code <- Right(lhs.code)
        maxyError <- Right(lhs.maxyError)
        person <- lhs.getPerson.fromGrpc
      } yield MaxyConnectionValidationError(code = code, maxyError = maxyError, person = person)
  }

}
