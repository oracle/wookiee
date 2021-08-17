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

  implicit class FooToGrpc(lhs: Foo) {

    def toGrpc: GrpcFoo = {
      val _ = lhs
      GrpcFoo()
    }
  }

  implicit class FooFromGrpc(lhs: GrpcFoo) {

    def fromGrpc: Either[String, Foo] = {
      val _ = lhs
      Right(Foo())
    }
  }

  implicit class TestToGrpc(lhs: Test) {

    def toGrpc: GrpcTest =
      GrpcTest(
        name = lhs.name,
        foo = lhs.foo.map(_.toGrpc),
        bar = lhs.bar,
        baz = lhs.baz.view.mapValues(_.toGrpc).toMap
      )
  }

  implicit class TestFromGrpc(lhs: GrpcTest) {

    def fromGrpc: Either[String, Test] =
      for {
        name <- Right(lhs.name.toList)
        foo <- lhs
          .foo
          .map(_.fromGrpc)
          .foldLeft(Right(Nil): Either[String, List[Foo]])({
            case (acc, i) =>
              i.flatMap(a => acc.map(b => a :: b))
          })
        bar <- Right(lhs.bar)
        baz <- Right(
          lhs
            .baz
            .view
            .mapValues(_.fromGrpc)
            .collect({
              case (a, Right(b)) =>
                (a, b)
            })
            .toMap
        )
      } yield Test(name = name, foo = foo, bar = bar, baz = baz)
  }

  implicit class PersonToGrpc(lhs: Person) {

    def toGrpc: GrpcPerson =
      GrpcPerson(name = lhs.name, age = lhs.age, optOpt = Some(lhs.optOpt.toGrpc), opt3 = Some(lhs.opt3.toGrpc))
  }

  implicit class PersonFromGrpc(lhs: GrpcPerson) {

    def fromGrpc: Either[String, Person] =
      for {
        name <- Right(lhs.name)
        age <- Right(lhs.age)
        optOpt <- lhs.getOptOpt.fromGrpc
        opt3 <- lhs.getOpt3.fromGrpc
      } yield Person(name = name, age = age, optOpt = optOpt, opt3 = opt3)
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

  implicit class OptionStringToGrpc(lhs: Option[String]) {

    def toGrpc: GrpcMaybeString =
      lhs match {
        case None =>
          GrpcMaybeString(GrpcMaybeString.OneOf.None(GrpcNone()))
        case Some(value) =>
          GrpcMaybeString(GrpcMaybeString.OneOf.Some(value))
      }
  }

  implicit class OptionStringFromGrpc(lhs: GrpcMaybeString) {

    def fromGrpc: Either[String, Option[String]] = lhs.oneOf match {
      case GrpcMaybeString.OneOf.Some(value) =>
        Right(Some(value))
      case _ =>
        Right(None)
    }
  }

  implicit class OptionTestToGrpc(lhs: Option[Test]) {

    def toGrpc: GrpcMaybeTest =
      lhs match {
        case None =>
          GrpcMaybeTest(GrpcMaybeTest.OneOf.None(GrpcNone()))
        case Some(value) =>
          GrpcMaybeTest(GrpcMaybeTest.OneOf.Some(value.toGrpc))
      }
  }

  implicit class OptionTestFromGrpc(lhs: GrpcMaybeTest) {

    def fromGrpc: Either[String, Option[Test]] = lhs.oneOf match {
      case GrpcMaybeTest.OneOf.Some(value) =>
        value.fromGrpc.map(Some(_))
      case _ =>
        Right(None)
    }
  }

  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {

    def toGrpc: GrpcMaybeMaybeString =
      lhs match {
        case None =>
          GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.None(GrpcNone()))
        case Some(value) =>
          GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.Some(value.toGrpc))
      }
  }

  implicit class OptionOptionStringFromGrpc(lhs: GrpcMaybeMaybeString) {

    def fromGrpc: Either[String, Option[Option[String]]] = lhs.oneOf match {
      case GrpcMaybeMaybeString.OneOf.Some(value) =>
        value.fromGrpc.map(Some(_))
      case _ =>
        Right(None)
    }
  }

}
