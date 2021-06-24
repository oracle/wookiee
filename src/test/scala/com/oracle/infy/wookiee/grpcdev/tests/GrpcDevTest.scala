package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgen.Model.Record
import com.oracle.infy.wookiee.grpc.srcgen.SrcGen
import com.oracle.infy.wookiee.grpcdev.common.TestModel.TestOptionString
import com.oracle.infy.wookiee.grpcdev.common._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

import scala.reflect.runtime.universe.{Symbol, typeOf}

object GrpcDevTest {

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val (mockRPCs, mockRecords, mockSealedTypeLookup) = MockObjectSets.EmptyMocks
      val service = srcGenTestObject.genService(mockRPCs, mockRecords, mockSealedTypeLookup, "foo.bar", "mockFoo")
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val (_, mockRecords, mockSealedTypeLookup) = MockObjectSets.EmptyMocks
      val scala = srcGenTestObject.genScala(
        mockRecords,
        mockSealedTypeLookup,
        "foo"
      )
      scala.trim.nonEmpty
    }

    def genProtoTest(typeSymbol: Symbol, expectedProto: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.genProto(List(srcGenTestObject.toRecord(typeSymbol)), sealedTypes)

      result.trim() === expectedProto.trim()
    }

    def grpcEncoderTest(typeSymbol: Symbol, expectedScala: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.grpcEncoderList(List(srcGenTestObject.toRecord(typeSymbol)), sealedTypes)

      result.trim() === expectedScala.trim()
    }

    def grpcDecoderTest(typeSymbol: Symbol, expectedScala: String) = {
      val sealedTypes = srcGenTestObject.sealedTypes(List(typeSymbol))
      val result = srcGenTestObject.grpcDecoderList(List(srcGenTestObject.toRecord(typeSymbol)), sealedTypes)

      result.trim() === expectedScala.trim()
    }

    // For the following tests for : genProto, grpcEncoder, grpcDecoder
    // [x] Option of scalar value
    // [ ] Option of a case class / custom value
    // [ ] Option of an option of a scalar value
    // [ ] Option of an option of a case class / custom value
    // Integration tests:
    // [ ] Use something _similar_ to Action Objects/case classes (but as mocks not exposing internal details on github
    //    as input to genScala and genService, craft expected generated code to compare (figure how to bypass whitespace compare)

    Tests {
      test("genService returns a non empty string") {
        assert(genServiceIsNonEmpty)
      }

      test("genScala returns a non empty string") {
        assert(genScalaIsNonEmpty)
      }

      test("genProto encodes Option[String] properly") {
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
    }
  }

  object srcGenTestObject extends SrcGen {
    def grpcEncoderList(records: List[Record], sealedTypes: Set[String]): String = {
      addOptionRecords(records, sealedTypes)
        .map(r => grpcEncoder(r, sealedTypes))
        .mkString("\n\n")
    }

    def grpcDecoderList(records: List[Record], sealedTypes: Set[String]): String = {
      addOptionRecords(records, sealedTypes)
        .map(r => grpcDecoder(r, sealedTypes))
        .mkString("\n\n")
    }
  }
}
