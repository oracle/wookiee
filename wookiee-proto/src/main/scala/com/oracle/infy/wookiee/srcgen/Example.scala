package com.oracle.infy.wookiee.srcgen

object Example {

  //todo -- this generates unnecessary methods in implicits, should be moved somewhere in the sourceGen package
  // but that is causing issues in other parts of the project
  final case class GrpcConversionError(msg: String)

  trait ASError

  trait DestinationError extends ASError
  trait ConnectionError extends ASError

  case class Foo()

  case class Test(name: List[String], foo: List[Foo], bar: Map[String, String], baz: Map[String, Foo])
  case class Person(name: String, age: Int, optOpt: Option[Option[String]], opt3: Option[Test])

  case class MaxyDestinationValidationError(code: Int, maxyError: String, person: Person, details: Option[String])
      extends DestinationError

  case class MaxyConnectionValidationError(code: Int, maxyError: String, person: Person)
      extends ConnectionError
      with DestinationError

}
