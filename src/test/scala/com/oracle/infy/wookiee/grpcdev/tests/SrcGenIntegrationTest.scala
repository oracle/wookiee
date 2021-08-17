package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgen.Model.{IO, Record}
import com.oracle.infy.wookiee.grpcdev.common.TestModel._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

import scala.reflect.runtime.universe.{Type, typeOf}

object SrcGenIntegrationTest {

  val srcGenInputs: (List[(Type, String)], List[Record], Set[String]) = {
    val types = List(
      typeOf[TestRequest],
      typeOf[TestMaybeResponse],
      typeOf[TestCaseClass],
      typeOf[TestOptionString],
      typeOf[TestOptionCaseClass],
      typeOf[TestOptionOptionString],
      typeOf[TestOptionOptionCaseClass]
    ).map(_.typeSymbol)

    val sealedTypeLookup = srcGenTestObject.sealedTypes(types)
    val records = types.map(srcGenTestObject.toRecord)

    val rpcs = List(
      typeOf[IO[String, String]] -> "testSimpleRpc",
      typeOf[IO[TestRequest, TestMaybeResponse]] -> "testComplexRpc"
    )

    (rpcs, records, sealedTypeLookup)
  }

  val testClassPackage = "some.test.class.package"
  val testServiceName = "TestService"

  val testScalaHeader =
    """
      |package some.test.class.package
      |
      |import com.oracle.infy.wookiee.grpcdev.common.TestModel._
      |import some.test.class.package._
      |""".stripMargin

  val expectedProto: String =
    s"""
      |// NOTE: This code is generated. DO NOT EDIT!!
      |syntax = "proto3";
      |
      |package $testClassPackage;
      |
      |
      |service $testServiceName {
      |
      |  // DO NOT EDIT! (this code is generated)
      |  rpc testSimpleRpc(GrpcString) returns (GrpcString) {}
      |
      |  // DO NOT EDIT! (this code is generated)
      |  rpc testComplexRpc(GrpcTestRequest) returns (GrpcTestMaybeResponse) {}
      |}
      |
      |
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestRequest {
      |  GrpcTestOptionOptionCaseClass testOptionOptionCaseClass = 1;
      |  GrpcTestOptionOptionString testOptionOptionString = 2;
      |  GrpcTestOptionCaseClass testOptionCaseClass = 3;
      |  GrpcTestOptionString testOptionString = 4;
      |  int32 testInt = 5;
      |  string testString = 6;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestMaybeResponse {
      |  oneof sealed_value {
      |    GrpcTestMaybeResponseCreated a = 1;
      |    GrpcTestMaybeResponseFailed b = 2;
      |    GrpcTestMaybeResponseInvalid c = 3;
      |  }
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestMaybeResponseCreated {
      |  GrpcTestCaseClass createdObject = 1;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestMaybeResponseFailed {
      |  string detail = 1;
      |  string msg = 2;
      |  int32 code = 3;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestMaybeResponseInvalid {
      |  repeated string errors = 1;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestCaseClass {
      |  int32 testInt = 1;
      |  string testString = 2;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestOptionString {
      |  GrpcOptionString maybeString = 1;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestOptionCaseClass {
      |  GrpcOptionTestCaseClass maybeCaseClass = 1;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestOptionOptionString {
      |  GrpcOptionOptionString maybeMaybeString = 1;
      |}
      |
      |// DO NOT EDIT! (this code is generated)
      |message GrpcTestOptionOptionCaseClass {
      |  GrpcOptionOptionTestCaseClass maybeMaybeCaseClass = 1;
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
      |""".stripMargin

  val expectedScala: String =
    s"""
      |$testScalaHeader
      |
      |import cats.implicits._
      |import scala.util.Try
      |
      |import java.time.{Instant, ZoneId, ZonedDateTime}
      |
      |// NOTE: This code is generated. DO NOT EDIT!
      |object implicits {
      |
      |  private def toZonedDateTime(l: Long): Either[GrpcConversionError, ZonedDateTime] = {
      |    Try {
      |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(l), ZoneId.of("UTC"))
      |    }.toEither
      |      .left.map(t => GrpcConversionError(t.getMessage))
      |  }
      |
      |  private def zonedDateTimeToLong(zdt: ZonedDateTime): Long = {
      |    zdt.toEpochSecond
      |  }
      |
      |  // Hack to "use" these private methods and cats.implicits so if generated code does not
      |  // use them, the compiler does not complain
      |  locally {val _ = zonedDateTimeToLong(toZonedDateTime(1L).getOrElse(ZonedDateTime.now())).combine(1L)}
      |
      |  implicit class TestRequestToGrpc(lhs: TestRequest) {
      |    def toGrpc: GrpcTestRequest = {
      |      GrpcTestRequest(
      |        testOptionOptionCaseClass = Some(lhs.testOptionOptionCaseClass.toGrpc),
      |        testOptionOptionString = Some(lhs.testOptionOptionString.toGrpc),
      |        testOptionCaseClass = Some(lhs.testOptionCaseClass.toGrpc),
      |        testOptionString = Some(lhs.testOptionString.toGrpc),
      |        testInt = lhs.testInt,
      |        testString = lhs.testString
      |      )
      |    }
      |  }
      |
      |  implicit class TestRequestFromGrpc(lhs: GrpcTestRequest) {
      |    def fromGrpc: Either[GrpcConversionError, TestRequest] = {
      |      for {
      |        testOptionOptionCaseClass <- lhs.getTestOptionOptionCaseClass.fromGrpc
      |        testOptionOptionString <- lhs.getTestOptionOptionString.fromGrpc
      |        testOptionCaseClass <- lhs.getTestOptionCaseClass.fromGrpc
      |        testOptionString <- lhs.getTestOptionString.fromGrpc
      |        testInt <- Right(lhs.testInt)
      |        testString <- Right(lhs.testString)
      |      } yield TestRequest(testOptionOptionCaseClass = testOptionOptionCaseClass,testOptionOptionString = testOptionOptionString,testOptionCaseClass = testOptionCaseClass,testOptionString = testOptionString,testInt = testInt,testString = testString)
      |    }
      |  }
      |  implicit class TestMaybeResponseToGrpc(lhs: TestMaybeResponse) {
      |    def toGrpc: GrpcTestMaybeResponse = {
      |      lhs match {
      |        case a: TestMaybeResponseCreated => a.toGrpc
      |        case b: TestMaybeResponseFailed => b.toGrpc
      |        case c: TestMaybeResponseInvalid => c.toGrpc
      |      }
      |    }
      |  }
      |  implicit class TestMaybeResponseCreatedToGrpc(lhs: TestMaybeResponseCreated) {
      |    def toGrpc: GrpcTestMaybeResponseCreated = {
      |      GrpcTestMaybeResponseCreated(
      |        createdObject = Some(lhs.createdObject.toGrpc)
      |      )
      |    }
      |  }
      |  implicit class TestMaybeResponseFailedToGrpc(lhs: TestMaybeResponseFailed) {
      |    def toGrpc: GrpcTestMaybeResponseFailed = {
      |      GrpcTestMaybeResponseFailed(
      |        detail = lhs.detail,
      |        msg = lhs.msg,
      |        code = lhs.code
      |      )
      |    }
      |  }
      |  implicit class TestMaybeResponseInvalidToGrpc(lhs: TestMaybeResponseInvalid) {
      |    def toGrpc: GrpcTestMaybeResponseInvalid = {
      |      GrpcTestMaybeResponseInvalid(
      |        errors = lhs.errors
      |      )
      |    }
      |  }
      |
      |  implicit class TestMaybeResponseFromGrpc(lhs: GrpcTestMaybeResponse) {
      |    def fromGrpc: Either[GrpcConversionError, TestMaybeResponse] = {
      |      None
      |        .orElse(lhs.asMessage.sealedValue.a.map(_.fromGrpc))
      |        .orElse(lhs.asMessage.sealedValue.b.map(_.fromGrpc))
      |        .orElse(lhs.asMessage.sealedValue.c.map(_.fromGrpc))
      |        .getOrElse(Left(GrpcConversionError("Invalid sealed values")))
      |    }
      |  }
      |  implicit class TestMaybeResponseCreatedFromGrpc(lhs: GrpcTestMaybeResponseCreated) {
      |    def fromGrpc: Either[GrpcConversionError, TestMaybeResponseCreated] = {
      |      for {
      |        createdObject <- lhs.getCreatedObject.fromGrpc
      |      } yield TestMaybeResponseCreated(createdObject = createdObject)
      |    }
      |  }
      |  implicit class TestMaybeResponseFailedFromGrpc(lhs: GrpcTestMaybeResponseFailed) {
      |    def fromGrpc: Either[GrpcConversionError, TestMaybeResponseFailed] = {
      |      for {
      |        detail <- Right(lhs.detail)
      |        msg <- Right(lhs.msg)
      |        code <- Right(lhs.code)
      |      } yield TestMaybeResponseFailed(detail = detail,msg = msg,code = code)
      |    }
      |  }
      |  implicit class TestMaybeResponseInvalidFromGrpc(lhs: GrpcTestMaybeResponseInvalid) {
      |    def fromGrpc: Either[GrpcConversionError, TestMaybeResponseInvalid] = {
      |      for {
      |        errors <- Right(lhs.errors.toList)
      |      } yield TestMaybeResponseInvalid(errors = errors)
      |    }
      |  }
      |  implicit class TestCaseClassToGrpc(lhs: TestCaseClass) {
      |    def toGrpc: GrpcTestCaseClass = {
      |      GrpcTestCaseClass(
      |        testInt = lhs.testInt,
      |        testString = lhs.testString
      |      )
      |    }
      |  }
      |
      |  implicit class TestCaseClassFromGrpc(lhs: GrpcTestCaseClass) {
      |    def fromGrpc: Either[GrpcConversionError, TestCaseClass] = {
      |      for {
      |        testInt <- Right(lhs.testInt)
      |        testString <- Right(lhs.testString)
      |      } yield TestCaseClass(testInt = testInt,testString = testString)
      |    }
      |  }
      |  implicit class TestOptionStringToGrpc(lhs: TestOptionString) {
      |    def toGrpc: GrpcTestOptionString = {
      |      GrpcTestOptionString(
      |        maybeString = lhs.maybeString.toGrpc
      |      )
      |    }
      |  }
      |
      |  implicit class TestOptionStringFromGrpc(lhs: GrpcTestOptionString) {
      |    def fromGrpc: Either[GrpcConversionError, TestOptionString] = {
      |      for {
      |        maybeString <- lhs.maybeString.fromGrpc
      |      } yield TestOptionString(maybeString = maybeString)
      |    }
      |  }
      |  implicit class TestOptionCaseClassToGrpc(lhs: TestOptionCaseClass) {
      |    def toGrpc: GrpcTestOptionCaseClass = {
      |      GrpcTestOptionCaseClass(
      |        maybeCaseClass = lhs.maybeCaseClass.toGrpc
      |      )
      |    }
      |  }
      |
      |  implicit class TestOptionCaseClassFromGrpc(lhs: GrpcTestOptionCaseClass) {
      |    def fromGrpc: Either[GrpcConversionError, TestOptionCaseClass] = {
      |      for {
      |        maybeCaseClass <- lhs.maybeCaseClass.fromGrpc
      |      } yield TestOptionCaseClass(maybeCaseClass = maybeCaseClass)
      |    }
      |  }
      |  implicit class TestOptionOptionStringToGrpc(lhs: TestOptionOptionString) {
      |    def toGrpc: GrpcTestOptionOptionString = {
      |      GrpcTestOptionOptionString(
      |        maybeMaybeString = lhs.maybeMaybeString.toGrpc
      |      )
      |    }
      |  }
      |
      |  implicit class TestOptionOptionStringFromGrpc(lhs: GrpcTestOptionOptionString) {
      |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionString] = {
      |      for {
      |        maybeMaybeString <- lhs.maybeMaybeString.fromGrpc
      |      } yield TestOptionOptionString(maybeMaybeString = maybeMaybeString)
      |    }
      |  }
      |  implicit class TestOptionOptionCaseClassToGrpc(lhs: TestOptionOptionCaseClass) {
      |    def toGrpc: GrpcTestOptionOptionCaseClass = {
      |      GrpcTestOptionOptionCaseClass(
      |        maybeMaybeCaseClass = lhs.maybeMaybeCaseClass.toGrpc
      |      )
      |    }
      |  }
      |
      |  implicit class TestOptionOptionCaseClassFromGrpc(lhs: GrpcTestOptionOptionCaseClass) {
      |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionCaseClass] = {
      |      for {
      |        maybeMaybeCaseClass <- lhs.maybeMaybeCaseClass.fromGrpc
      |      } yield TestOptionOptionCaseClass(maybeMaybeCaseClass = maybeMaybeCaseClass)
      |    }
      |  }
      |  implicit class OptionStringToGrpc(lhs: Option[String]) {
      |    def toGrpc: GrpcOptionString = {
      |      lhs match {
      |        case None =>  GrpcNoneString()
      |        case Some(v) => GrpcSomeString(v)
      |      }
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
      |  implicit class OptionTestCaseClassToGrpc(lhs: Option[TestCaseClass]) {
      |    def toGrpc: GrpcOptionTestCaseClass = {
      |      lhs match {
      |        case None =>  GrpcNoneTestCaseClass()
      |        case Some(v) => GrpcSomeTestCaseClass(Some(v.toGrpc))
      |      }
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
      |  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
      |    def toGrpc: GrpcOptionOptionString = {
      |       lhs match {
      |         case None =>  GrpcNoneNoneString()
      |         case Some(Some(v)) => GrpcSomeSomeString(v)
      |         case Some(None) => GrpcSomeNoneString()
      |       }
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
      |  implicit class OptionOptionTestCaseClassToGrpc(lhs: Option[Option[TestCaseClass]]) {
      |    def toGrpc: GrpcOptionOptionTestCaseClass = {
      |       lhs match {
      |         case None =>  GrpcNoneNoneTestCaseClass()
      |         case Some(Some(v)) => GrpcSomeSomeTestCaseClass(Some(v.toGrpc))
      |         case Some(None) => GrpcSomeNoneTestCaseClass()
      |       }
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
      |}
      |""".stripMargin

  val genServiceTest: Boolean = {
    val (rpcs, records, sealedTypeLookup) = srcGenInputs
    val proto = srcGenTestObject.genService(
      rpcs,
      records,
      sealedTypeLookup,
      testClassPackage,
      testServiceName
    )

    proto.trim() === expectedProto.trim()
  }

  val genScalaTest: Boolean = {
    val (_, records, sealedTypeLookup) = srcGenInputs
    val scala = srcGenTestObject.genScala(
      records,
      sealedTypeLookup,
      testScalaHeader
    )

    scala.trim() === expectedScala.trim()
  }

  def tests(): Tests =
    Tests {
      test("genService (proto file) integration test") {
        assert(genServiceTest)
      }
      test("genScala (implicits) integration test") {
        assert(genScalaTest)
      }
    }
}
