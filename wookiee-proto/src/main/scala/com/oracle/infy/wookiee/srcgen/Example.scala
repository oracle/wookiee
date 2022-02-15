package com.oracle.infy.wookiee.srcgen

import java.time.ZonedDateTime

object Example {

  @srcGenIgnoreClass
  final case class srcGenIgnoreClass() extends scala.annotation.StaticAnnotation

  @srcGenIgnoreClass
  final case class srcGenIgnoreField(field: String) extends scala.annotation.StaticAnnotation

  @srcGenIgnoreClass
  final case class GrpcConversionError(msg: String)

  @srcGenIgnoreClass
  final case class IgnoreThisClass()

  trait ASError

  trait DestinationError extends ASError
  trait ConnectionError extends ASError

  case class Foo()

  case class Test(
      name: List[String],
      foo: List[Foo],
      bar: Map[String, String],
      baz: Map[String, Foo],
      opt0: Option[List[String]]
  )

  case class Person(
      name: String,
      age: Int,
      optOpt: Option[Option[String]],
      opt3: Option[Test]
  )

  case class Watch(
      time: ZonedDateTime,
      alarms: List[ZonedDateTime],
      optionTime: Option[ZonedDateTime]
  )

  case class MaxyDestinationValidationError(code: Int, maxyError: String, person: Person, details: Option[String])
      extends DestinationError

  case class MaxyConnectionValidationError(code: Int, maxyError: String, person: Person)
      extends ConnectionError
      with DestinationError

}
