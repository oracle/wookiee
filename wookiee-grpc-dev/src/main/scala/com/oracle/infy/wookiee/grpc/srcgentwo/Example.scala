package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgen.Model.srcGenIgnore

object Example {

  sealed trait ASError

  sealed trait DestinationError extends ASError

  case class Person(name: String, age: Int)

  @srcGenIgnore("maxyError")
  case class ValidationError(code: Int, maxyError: String, person: Person) extends DestinationError

}
