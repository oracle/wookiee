package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpcdev.common.TestModel._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

import scala.reflect.runtime.universe.{Symbol, typeOf}

object GrpcDevTest {

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val service = srcGenTestObject.genService(List(), List(), Set(), "foo.bar", "Baz")
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val scala = srcGenTestObject.genScala(List(), Set(), "foo")
      scala.trim.nonEmpty
    }

    def genProtoTest(typeSymbol: Symbol, expectedProto: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.genProto(List(srcGenTestObject.toRecord(typeSymbol)), sealedTypes)

      result.trim() === expectedProto.trim()
    }

    def grpcEncoderTest(typeSymbol: Symbol, expectedScala: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.grpcEncoderWithOptions(srcGenTestObject.toRecord(typeSymbol), sealedTypes)

      result.trim() === expectedScala.trim()
    }

    def grpcDecoderTest(typeSymbol: Symbol, expectedScala: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.grpcDecoderWithOptions(srcGenTestObject.toRecord(typeSymbol), sealedTypes)

      result.trim() === expectedScala.trim()
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
          typeOf[TestOptionString].typeSymbol,
          """|// DO NOT EDIT! (this code is generated)
             |message GrpcTestOptionString {
             |  GrpcOptionString maybeString = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcOptionString {
             |  oneof sealed_value {
             |    GrpcNoneString a = 1;
             |    GrpcSomeString b = 2;
             |  }
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcNoneString {
             |
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeString {
             |  string value = 1;
             |}
             |""".stripMargin
        )
        assert(result)
      }

      test("genProto constructs proto of Option[CaseClass] properly") {
        val result = genProtoTest(
          typeOf[TestOptionCaseClass].typeSymbol,
          """|
             |// DO NOT EDIT! (this code is generated)
             |message GrpcTestOptionCaseClass {
             |  GrpcOptionTestCaseClass maybeCaseClass = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcOptionTestCaseClass {
             |  oneof sealed_value {
             |    GrpcNoneTestCaseClass a = 1;
             |    GrpcSomeTestCaseClass b = 2;
             |  }
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcNoneTestCaseClass {
             |
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeTestCaseClass {
             |  GrpcTestCaseClass value = 1;
             |}
             |""".stripMargin
        )
        assert(result)
      }

      test("genProto constructs proto of Option[Option[String]] properly") {
        val result = genProtoTest(
          typeOf[TestOptionOptionString].typeSymbol,
          """|
             |// DO NOT EDIT! (this code is generated)
             |message GrpcTestOptionOptionString {
             |  GrpcOptionOptionString maybeMaybeString = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcOptionOptionString {
             |  oneof sealed_value {
             |    GrpcNoneNoneString a = 1;
             |    GrpcSomeSomeString b = 2;
             |    GrpcSomeNoneString c = 3;
             |  }
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcNoneNoneString {
             |
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeSomeString {
             |  string value = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeNoneString {
             |
             |}
             |""".stripMargin
        )
        assert(result)
      }

      test("genProto constructs proto of Option[Option[CaseClass]] properly") {
        val result = genProtoTest(
          typeOf[TestOptionOptionCaseClass].typeSymbol,
          """|
             |// DO NOT EDIT! (this code is generated)
             |message GrpcTestOptionOptionCaseClass {
             |  GrpcOptionOptionTestCaseClass maybeMaybeCaseClass = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcOptionOptionTestCaseClass {
             |  oneof sealed_value {
             |    GrpcNoneNoneTestCaseClass a = 1;
             |    GrpcSomeSomeTestCaseClass b = 2;
             |    GrpcSomeNoneTestCaseClass c = 3;
             |  }
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcNoneNoneTestCaseClass {
             |
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeSomeTestCaseClass {
             |  GrpcTestCaseClass value = 1;
             |}
             |
             |// DO NOT EDIT! (this code is generated)
             |message GrpcSomeNoneTestCaseClass {
             |
             |}
             |
             |""".stripMargin
        )
        assert(result)
      }

      //grpcEncoder Tests
      test("grpcEncoder encodes Option[String] properly") {
        val result = grpcEncoderTest(
          typeOf[TestOptionString].typeSymbol,
          """|  implicit class TestOptionStringToGrpc(lhs: TestOptionString) {
             |    def toGrpc: GrpcTestOptionString = {
             |      GrpcTestOptionString(
             |        maybeString = lhs.maybeString.toGrpc
             |      )
             |    }
             |  }
             |
             |  implicit class OptionStringToGrpc(lhs: Option[String]) {
             |    def toGrpc: GrpcOptionString = {
             |      lhs match {
             |        case None =>  GrpcNoneString()
             |        case Some(v) => GrpcSomeString(v)
             |      }
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcEncoder encodes Option[CaseClass] properly") {
        val result = grpcEncoderTest(
          typeOf[TestOptionCaseClass].typeSymbol,
          """|  implicit class TestOptionCaseClassToGrpc(lhs: TestOptionCaseClass) {
             |    def toGrpc: GrpcTestOptionCaseClass = {
             |      GrpcTestOptionCaseClass(
             |        maybeCaseClass = lhs.maybeCaseClass.toGrpc
             |      )
             |    }
             |  }
             |
             |  implicit class OptionTestCaseClassToGrpc(lhs: Option[TestCaseClass]) {
             |    def toGrpc: GrpcOptionTestCaseClass = {
             |      lhs match {
             |        case None =>  GrpcNoneTestCaseClass()
             |        case Some(v) => GrpcSomeTestCaseClass(Some(v.toGrpc))
             |      }
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcEncoder encodes Option[Option[String]] properly") {
        val result = grpcEncoderTest(
          typeOf[TestOptionOptionString].typeSymbol,
          """|  implicit class TestOptionOptionStringToGrpc(lhs: TestOptionOptionString) {
             |    def toGrpc: GrpcTestOptionOptionString = {
             |      GrpcTestOptionOptionString(
             |        maybeMaybeString = lhs.maybeMaybeString.toGrpc
             |      )
             |    }
             |  }
             |
             |  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
             |    def toGrpc: GrpcOptionOptionString = {
             |       lhs match {
             |         case None =>  GrpcNoneNoneString()
             |         case Some(Some(v)) => GrpcSomeSomeString(v)
             |         case Some(None) => GrpcSomeNoneString()
             |       }
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcEncoder encodes Option[Option[CaseClass]] properly") {
        val result = grpcEncoderTest(
          typeOf[TestOptionOptionCaseClass].typeSymbol,
          """|  implicit class TestOptionOptionCaseClassToGrpc(lhs: TestOptionOptionCaseClass) {
             |    def toGrpc: GrpcTestOptionOptionCaseClass = {
             |      GrpcTestOptionOptionCaseClass(
             |        maybeMaybeCaseClass = lhs.maybeMaybeCaseClass.toGrpc
             |      )
             |    }
             |  }
             |
             |  implicit class OptionOptionTestCaseClassToGrpc(lhs: Option[Option[TestCaseClass]]) {
             |    def toGrpc: GrpcOptionOptionTestCaseClass = {
             |       lhs match {
             |         case None =>  GrpcNoneNoneTestCaseClass()
             |         case Some(Some(v)) => GrpcSomeSomeTestCaseClass(Some(v.toGrpc))
             |         case Some(None) => GrpcSomeNoneTestCaseClass()
             |       }
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      //grpcDecoder Tests
      test("grpcDecoder decodes Option[String] properly") {
        val result = grpcDecoderTest(
          typeOf[TestOptionString].typeSymbol,
          """|  implicit class TestOptionStringFromGrpc(lhs: GrpcTestOptionString) {
             |    def fromGrpc: Either[GrpcConversionError, TestOptionString] = {
             |      for {
             |        maybeString <- lhs.maybeString.fromGrpc
             |      } yield TestOptionString(maybeString = maybeString)
             |    }
             |  }
             |
             |  implicit class OptionStringFromGrpc(lhs: GrpcOptionString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
             |      None
             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
             |    }
             |  }
             |  implicit class NoneStringFromGrpc(lhs: GrpcNoneString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
             |      val _ = lhs
             |      Right(None)
             |    }
             |  }
             |  implicit class SomeStringFromGrpc(lhs: GrpcSomeString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[String]] = {
             |      for {
             |        value <- Right(lhs.value)
             |      } yield Option(value)
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcDecoder decodes Option[CaseClass] properly") {
        val result = grpcDecoderTest(
          typeOf[TestOptionCaseClass].typeSymbol,
          """|  implicit class TestOptionCaseClassFromGrpc(lhs: GrpcTestOptionCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, TestOptionCaseClass] = {
             |      for {
             |        maybeCaseClass <- lhs.maybeCaseClass.fromGrpc
             |      } yield TestOptionCaseClass(maybeCaseClass = maybeCaseClass)
             |    }
             |  }
             |
             |  implicit class OptionTestCaseClassFromGrpc(lhs: GrpcOptionTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
             |      None
             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
             |    }
             |  }
             |  implicit class NoneTestCaseClassFromGrpc(lhs: GrpcNoneTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
             |      val _ = lhs
             |      Right(None)
             |    }
             |  }
             |  implicit class SomeTestCaseClassFromGrpc(lhs: GrpcSomeTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = {
             |      for {
             |        value <- lhs.getValue.fromGrpc
             |      } yield Option(value)
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcDecoder decodes Option[Option[String]] properly") {
        val result = grpcDecoderTest(
          typeOf[TestOptionOptionString].typeSymbol,
          """|  implicit class TestOptionOptionStringFromGrpc(lhs: GrpcTestOptionOptionString) {
             |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionString] = {
             |      for {
             |        maybeMaybeString <- lhs.maybeMaybeString.fromGrpc
             |      } yield TestOptionOptionString(maybeMaybeString = maybeMaybeString)
             |    }
             |  }
             |
             |  implicit class OptionOptionStringFromGrpc(lhs: GrpcOptionOptionString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
             |      None
             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.c.map(_.fromGrpc))
             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
             |    }
             |  }
             |  implicit class NoneNoneStringFromGrpc(lhs: GrpcNoneNoneString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
             |      val _ = lhs
             |      Right(None)
             |    }
             |  }
             |  implicit class SomeSomeStringFromGrpc(lhs: GrpcSomeSomeString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
             |      for {
             |        value <- Right(lhs.value)
             |      } yield Option(Option(value))
             |    }
             |  }
             |  implicit class SomeNoneStringFromGrpc(lhs: GrpcSomeNoneString) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = {
             |      val _ = lhs
             |      Right(Some(None))
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }

      test("grpcDecoder decodes Option[Option[CaseClass]] properly") {
        val result = grpcDecoderTest(
          typeOf[TestOptionOptionCaseClass].typeSymbol,
          """|  implicit class TestOptionOptionCaseClassFromGrpc(lhs: GrpcTestOptionOptionCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionCaseClass] = {
             |      for {
             |        maybeMaybeCaseClass <- lhs.maybeMaybeCaseClass.fromGrpc
             |      } yield TestOptionOptionCaseClass(maybeMaybeCaseClass = maybeMaybeCaseClass)
             |    }
             |  }
             |
             |  implicit class OptionOptionTestCaseClassFromGrpc(lhs: GrpcOptionOptionTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
             |      None
             |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
             |        .orElse(lhs.asMessage.sealedValue.c.map(_.fromGrpc))
             |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
             |    }
             |  }
             |  implicit class NoneNoneTestCaseClassFromGrpc(lhs: GrpcNoneNoneTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
             |      val _ = lhs
             |      Right(None)
             |    }
             |  }
             |  implicit class SomeSomeTestCaseClassFromGrpc(lhs: GrpcSomeSomeTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
             |      for {
             |        value <- lhs.getValue.fromGrpc
             |      } yield Option(Option(value))
             |    }
             |  }
             |  implicit class SomeNoneTestCaseClassFromGrpc(lhs: GrpcSomeNoneTestCaseClass) {
             |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = {
             |      val _ = lhs
             |      Right(Some(None))
             |    }
             |  }
             |""".stripMargin
        )
        assert(result)
      }
    }
  }
}
