package com.oracle.infy.wookiee.grpcdev.common

import com.oracle.infy.wookiee.grpc.srcgen.Model.Record
import com.oracle.infy.wookiee.grpc.srcgen.SrcGen

object TestModel {
  sealed trait TestMaybeResponse

  case class TestCaseClass(testString: String, testInt: Int)

  case class TestOptionString(maybeString: Option[String])

  case class TestOptionCaseClass(maybeCaseClass: Option[TestCaseClass])

  case class TestOptionOptionString(maybeMaybeString: Option[Option[String]])

  case class TestOptionOptionCaseClass(maybeMaybeCaseClass: Option[Option[TestCaseClass]])

  final case class TestRequest(
      testString: String,
      testInt: Int,
      testOptionString: TestOptionString,
      testOptionCaseClass: TestOptionCaseClass,
      testOptionOptionString: TestOptionOptionString,
      testOptionOptionCaseClass: TestOptionOptionCaseClass
  )

  final case class TestMaybeResponseCreated(createdObject: TestCaseClass) extends TestMaybeResponse
  final case class TestMaybeResponseFailed(code: Int, msg: String, detail: String) extends TestMaybeResponse
  final case class TestMaybeResponseInvalid(errors: List[String]) extends TestMaybeResponse

  object srcGenTestObject extends SrcGen {

    def grpcEncoderWithOptions(record: Record, sealedTypes: Set[String]): String = {
      addOptionRecords(List(record), sealedTypes)
        .map(r => grpcEncoder(r, sealedTypes))
        .mkString("\n\n")
    }

    def grpcDecoderWithOptions(record: Record, sealedTypes: Set[String]): String = {
      addOptionRecords(List(record), sealedTypes)
        .map(r => grpcDecoder(r, sealedTypes))
        .mkString("\n\n")
    }
  }
}
