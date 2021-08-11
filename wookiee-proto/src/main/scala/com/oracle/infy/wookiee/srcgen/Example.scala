package com.oracle.infy.wookiee.srcgen

object Example {

  trait ASError

  trait DestinationError extends ASError
  trait ConnectionError extends ASError

  @hasOption("middleName")
  case class Person(name: String, age: Int, middleName: Option[String])

  case class MaxyDestinationValidationError(code: Int, maxyError: String, person: Person) extends DestinationError

  case class MaxyConnectionValidationError(code: Int, maxyError: String, person: Person)
      extends ConnectionError
      with DestinationError

}
