package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.srcgen.implicits._
import com.oracle.infy.wookiee.srcgen.Example._
import com.oracle.infy.wookiee.utils.implicits._
import cats.implicits._
import utest.{Tests, test}

object SrcGenTwoTest {

  def tests(): Tests = {

    def symmetryTest: Boolean = {
      val destError: MaxyDestinationValidationError =
        MaxyDestinationValidationError(4, "err", Person("Ladinu", 100, Some(None), None), None)
      val asError: ASError = destError
      val grpcDestError = asError.toGrpc

      grpcDestError.fromGrpc.map(err => err === destError).leftMap(_ => false).merge
    }

    Tests {
      test("toGrpc and fromGrpc are symmetric") {
        assert(symmetryTest)
      }
    }

  }

  def main(args: Array[String]): Unit = {}

}
