package com.oracle.infy.wookiee.srcgen

object Example {

  @srcGenIgnoreClass
  final case class srcGenIgnoreClass() extends scala.annotation.StaticAnnotation

  @srcGenIgnoreClass
  final case class srcGenIgnoreField(field: String) extends scala.annotation.StaticAnnotation

  @srcGenIgnoreClass
  final case class GrpcConversionError(msg: String)

  @srcGenIgnoreClass
  final case class IgnoreThisClass(msg: String)

  trait ASError

  trait DestinationError extends ASError
  trait ConnectionError extends ASError

  case class Foo()

  @srcGenIgnoreField("ignoreMe")
  case class Test(
      name: List[String],
      foo: List[Foo],
      bar: Map[String, String],
      baz: Map[String, Foo],
      ignoreMe: IgnoreThisClass
  )

  case class Person(name: String, age: Int, optOpt: Option[Option[String]], opt3: Option[Test])

  case class MaxyDestinationValidationError(code: Int, maxyError: String, person: Person, details: Option[String])
      extends DestinationError

  case class MaxyConnectionValidationError(code: Int, maxyError: String, person: Person)
      extends ConnectionError
      with DestinationError

}
