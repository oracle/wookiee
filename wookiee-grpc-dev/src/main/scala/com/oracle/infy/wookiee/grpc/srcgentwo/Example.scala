package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgen.Model.srcGenIgnore

object Example {

  sealed trait ASError

  sealed trait DestinationError extends ASError

  @srcGenIgnore("maxyError")
  case class ValidationError(code: Int, maxyError: String) extends DestinationError

}
