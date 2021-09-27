package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgentwo.SourceGenModel.ScalaTextSource
import com.oracle.infy.wookiee.grpcdev.common.TestModel._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

object GrpcDevTest {

  def genProtoTest(source: String, expectedProto: String): Boolean = {
    val result = srcGenTestObject.genProto(Nil, Nil, ScalaTextSource(source) :: Nil)
    val ret = result.trim() === expectedProto.trim()
    ret
  }

  def genScalaTest(source: String, expectedScala: String): Boolean = {
    val result = srcGenTestObject.genScala(Nil, ScalaTextSource(source) :: Nil)
    val ret = result.trim() === expectedScala.trim()
    ret
  }

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val service = srcGenTestObject.genProto(Nil, Nil, Nil)
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val scala = srcGenTestObject.genScala(Nil, Nil)
      scala.trim.nonEmpty
    }

    Tests {
      test("genService returns a non empty string") {
        assert(genServiceIsNonEmpty)
      }

      test("genScala returns a non empty string") {
        assert(genScalaIsNonEmpty)
      }

      //genProto Tests
      test("genProto constructs proto of Option[String] properly") {

        val result = genProtoTest(
          "case class TestOptionString(maybeString: Option[String])",
          GrpcDevTestResults.genProtoOptionStringResult
        )
        assert(result)
      }

      test("genProto constructs proto of Option[CaseClass] properly") {
        val result = genProtoTest(
          """case class TestOptionCaseClass(maybeCaseClass: Option[TestCaseClass])
             case class TestCaseClass(testString: String, testInt: Int)""",
          GrpcDevTestResults.genProtoOptionCaseClassResult
        )
        assert(result)
      }

      test("genProto constructs proto of Option[Option[String]] properly") {
        val result = genProtoTest(
          "case class TestOptionOptionString(maybeMaybeString: Option[Option[String]])",
          GrpcDevTestResults.genProtoOptionOptionStringResult
        )
        assert(result)
      }

      test("genProto constructs proto of Option[Option[CaseClass]] properly") {
        val result = genProtoTest(
          "case class TestOptionOptionCaseClass(maybeMaybeCaseClass: Option[Option[TestCaseClass]])",
          GrpcDevTestResults.genProtoOptionOptionCaseClassResult
        )
        assert(result)
      }

      // genScala Tests
      test("genScala generates Option[String] properly") {
        val result = genScalaTest(
          "case class TestOptionString(maybeString: Option[String])",
          GrpcDevTestResults.genScalaOptionStringResult
        )
        assert(result)
      }

      test("genScala generates Option[CaseClass] properly") {
        val result = genScalaTest(
          """case class TestOptionCaseClass(maybeCaseClass: Option[TestCaseClass])
             case class TestCaseClass(testString: String, testInt: Int)""",
          GrpcDevTestResults.genScalaOptionCaseClassResult
        )
        assert(result)
      }

      test("genScala generates Option[Option[String]] properly") {
        val result = genScalaTest(
          "case class TestOptionOptionString(maybeMaybeString: Option[Option[String]])",
          GrpcDevTestResults.genScalaOptionOptionStringResult
        )
        assert(result)
      }

      test("grpcEncoder encodes Option[Option[CaseClass]] properly") {
        val result = genScalaTest(
          "case class TestOptionOptionCaseClass(maybeMaybeCaseClass: Option[Option[TestCaseClass]])",
          GrpcDevTestResults.genScalaOptionOptionCaseClassResult
        )
        assert(result)
      }
    }
  }
}
