package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgentwo.SourceGenModel.ScalaTextSource
import com.oracle.infy.wookiee.grpcdev.common.TestModel._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

object GrpcDevTest {

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val service = srcGenTestObject.genProto(Nil, Nil, Nil)
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val scala = srcGenTestObject.genScala(Nil, Nil)
      scala.trim.nonEmpty
    }

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

      test("grpcEncoder encodes Option[CaseClass] properly") {
        val result = genScalaTest(
          """case class TestOptionCaseClass(maybeCaseClass: Option[TestCaseClass])
             case class TestCaseClass(testString: String, testInt: Int)""",
          GrpcDevTestResults.genScalaOptionCaseClassResult
        )
        assert(result)
      }
//
//      test("grpcEncoder encodes Option[Option[String]] properly") {
//        val result = grpcEncoderTest(
//          typeOf[TestOptionOptionString].typeSymbol,
//          """|  implicit class TestOptionOptionStringToGrpc(lhs: TestOptionOptionString) {
//             |    def toGrpc: GrpcTestOptionOptionString = {
//             |      GrpcTestOptionOptionString(
//             |        maybeMaybeString = lhs.maybeMaybeString.toGrpc
//             |      )
//             |    }
//             |  }
//             |
//             |  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
//             |    def toGrpc: GrpcOptionOptionString = {
//             |       lhs match {
//             |         case None =>  GrpcNoneNoneString()
//             |         case Some(Some(v)) => GrpcSomeSomeString(v)
//             |         case Some(None) => GrpcSomeNoneString()
//             |       }
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
//
//      test("grpcEncoder encodes Option[Option[CaseClass]] properly") {
//        val result = grpcEncoderTest(
//          typeOf[TestOptionOptionCaseClass].typeSymbol,
//          """|  implicit class TestOptionOptionCaseClassToGrpc(lhs: TestOptionOptionCaseClass) {
//             |    def toGrpc: GrpcTestOptionOptionCaseClass = {
//             |      GrpcTestOptionOptionCaseClass(
//             |        maybeMaybeCaseClass = lhs.maybeMaybeCaseClass.toGrpc
//             |      )
//             |    }
//             |  }
//             |
//             |  implicit class OptionOptionTestCaseClassToGrpc(lhs: Option[Option[TestCaseClass]]) {
//             |    def toGrpc: GrpcOptionOptionTestCaseClass = {
//             |       lhs match {
//             |         case None =>  GrpcNoneNoneTestCaseClass()
//             |         case Some(Some(v)) => GrpcSomeSomeTestCaseClass(Some(v.toGrpc))
//             |         case Some(None) => GrpcSomeNoneTestCaseClass()
//             |       }
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
//
//      //grpcDecoder Tests
//      test("grpcDecoder decodes Option[String] properly") {
//        val result = grpcDecoderTest(
//          typeOf[TestOptionString].typeSymbol,
//          """|  implicit class TestOptionStringFromGrpc(lhs: GrpcTestOptionString) {
//             |    def fromGrpc: Either[GrpcConversionError, TestOptionString] = {
//             |      for {
//             |        maybeString <- lhs.maybeString.fromGrpc
//             |      } yield TestOptionString(maybeString = maybeString)
//             |    }
//             |  }
//             |
//             |  implicit class OptionStringFromGrpc(lhs: GrpcOptionString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
//             |      None
//             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
//             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
//             |    }
//             |  }
//             |  implicit class NoneStringFromGrpc(lhs: GrpcNoneString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
//             |      val _ = lhs
//             |      Right(None)
//             |    }
//             |  }
//             |  implicit class SomeStringFromGrpc(lhs: GrpcSomeString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
//             |      for {
//             |        value <- Right(lhs.value)
//             |      } yield Option(value)
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
//
//      test("grpcDecoder decodes Option[CaseClass] properly") {
//        val result = grpcDecoderTest(
//          typeOf[TestOptionCaseClass].typeSymbol,
//          """|  implicit class TestOptionCaseClassFromGrpc(lhs: GrpcTestOptionCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, TestOptionCaseClass] = {
//             |      for {
//             |        maybeCaseClass <- lhs.maybeCaseClass.fromGrpc
//             |      } yield TestOptionCaseClass(maybeCaseClass = maybeCaseClass)
//             |    }
//             |  }
//             |
//             |  implicit class OptionTestCaseClassFromGrpc(lhs: GrpcOptionTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
//             |      None
//             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
//             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
//             |    }
//             |  }
//             |  implicit class NoneTestCaseClassFromGrpc(lhs: GrpcNoneTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
//             |      val _ = lhs
//             |      Right(None)
//             |    }
//             |  }
//             |  implicit class SomeTestCaseClassFromGrpc(lhs: GrpcSomeTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
//             |      for {
//             |        value <- lhs.getValue.fromGrpc
//             |      } yield Option(value)
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
//
//      test("grpcDecoder decodes Option[Option[String]] properly") {
//        val result = grpcDecoderTest(
//          typeOf[TestOptionOptionString].typeSymbol,
//          """|  implicit class TestOptionOptionStringFromGrpc(lhs: GrpcTestOptionOptionString) {
//             |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionString] = {
//             |      for {
//             |        maybeMaybeString <- lhs.maybeMaybeString.fromGrpc
//             |      } yield TestOptionOptionString(maybeMaybeString = maybeMaybeString)
//             |    }
//             |  }
//             |
//             |  implicit class OptionOptionStringFromGrpc(lhs: GrpcOptionOptionString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
//             |      None
//             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.c.map(_.fromGrpc))
//             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
//             |    }
//             |  }
//             |  implicit class NoneNoneStringFromGrpc(lhs: GrpcNoneNoneString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
//             |      val _ = lhs
//             |      Right(None)
//             |    }
//             |  }
//             |  implicit class SomeSomeStringFromGrpc(lhs: GrpcSomeSomeString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
//             |      for {
//             |        value <- Right(lhs.value)
//             |      } yield Option(Option(value))
//             |    }
//             |  }
//             |  implicit class SomeNoneStringFromGrpc(lhs: GrpcSomeNoneString) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
//             |      val _ = lhs
//             |      Right(Some(None))
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
//
//      test("grpcDecoder decodes Option[Option[CaseClass]] properly") {
//        val result = grpcDecoderTest(
//          typeOf[TestOptionOptionCaseClass].typeSymbol,
//          """|  implicit class TestOptionOptionCaseClassFromGrpc(lhs: GrpcTestOptionOptionCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionCaseClass] = {
//             |      for {
//             |        maybeMaybeCaseClass <- lhs.maybeMaybeCaseClass.fromGrpc
//             |      } yield TestOptionOptionCaseClass(maybeMaybeCaseClass = maybeMaybeCaseClass)
//             |    }
//             |  }
//             |
//             |  implicit class OptionOptionTestCaseClassFromGrpc(lhs: GrpcOptionOptionTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
//             |      None
//             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
//             |        .orElse(lhs.asMessage.sealedValue.c.map(_.fromGrpc))
//             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
//             |    }
//             |  }
//             |  implicit class NoneNoneTestCaseClassFromGrpc(lhs: GrpcNoneNoneTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
//             |      val _ = lhs
//             |      Right(None)
//             |    }
//             |  }
//             |  implicit class SomeSomeTestCaseClassFromGrpc(lhs: GrpcSomeSomeTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
//             |      for {
//             |        value <- lhs.getValue.fromGrpc
//             |      } yield Option(Option(value))
//             |    }
//             |  }
//             |  implicit class SomeNoneTestCaseClassFromGrpc(lhs: GrpcSomeNoneTestCaseClass) {
//             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
//             |      val _ = lhs
//             |      Right(Some(None))
//             |    }
//             |  }
//             |""".stripMargin
//        )
//        assert(result)
//      }
    }
  }
}
