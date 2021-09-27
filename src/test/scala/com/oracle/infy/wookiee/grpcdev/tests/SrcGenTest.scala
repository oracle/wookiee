package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.srcgen.implicits._
import com.oracle.infy.wookiee.srcgen.Example._
import com.oracle.infy.wookiee.utils.implicits._
import cats.implicits._
import utest.{Tests, test}

object SrcGenTest {

  def tests(): Tests = {

    def symmetryTestOptions: Boolean = {
      val destError: MaxyDestinationValidationError =
        MaxyDestinationValidationError(4, "err", Person("Ladinu", 100, Some(Some("something")), None), None)
      val asError: ASError = destError
      val grpcDestError = asError.toGrpc

      grpcDestError.fromGrpc.map(err => err === destError).leftMap(_ => false).merge
    }

    def symmetryTestMaps: Boolean = {
      val testObj = Test(
        List("Max", "Ladinu"),
        List(Foo()),
        Map("foo" -> "bar"),
        Map("baz" -> Foo(), "bar" -> Foo()),
        Some(List("test"))
      )

      val grpcTestObj = testObj.toGrpc

      grpcTestObj.fromGrpc.map(err => err === testObj).leftMap(_ => false).merge
    }

    Tests {
      test("toGrpc and fromGrpc are symmetric with options") {
        assert(symmetryTestOptions)
      }

      test("toGrpc and fromGrpc are symmetric with maps") {
        assert(symmetryTestMaps)
      }
    }

  }

}
