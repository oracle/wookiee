package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.srcgen.implicits._
import com.oracle.infy.wookiee.srcgen.Example._
import com.oracle.infy.wookiee.utils.implicits._
import cats.implicits._

object SrcGenTwoTest {

  def main(args: Array[String]): Unit = {
    val destError: MaxyDestinationValidationError = MaxyDestinationValidationError(4, "err", Person("Ladinu", 100))
    val asError: ASError = destError
    val grpcDestError = asError.toGrpc

    grpcDestError.fromGrpc.map(err => assert(err === destError)).leftMap(_ => assert(false)).merge

  }

}
