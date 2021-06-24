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

    // For the following tests for : genProto, grpcEncoder, grpcDecoder
    // Option of scalar value
    // Option of a case class / custom value
    // Option of an option of a scalar value
    // Option of an option of a case class / custom value
    // Stretch testing goal property based test:
    //  Generate an instance of scala case class, run toGRPC on that case class, call toADR on the output of that,
    //  compare that result w/ the original instance of the case class
    // Scalacheck library can be used for property based testing
    // Integration tests:
    // Use something similar to Action Objects/case classes as input to genScala and genService, craft expected generated code to compare (figure how to bypass whitespace compare)

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

//      test("Gen Scala returns a non empty string") {
//        //TODO Parameterize tests
//        assert(testCodeGen(types, expectedvalues))
//      }

    }
  }

  object srcGenTestObject extends SrcGen {
    def grpcEncoderList(records: List[Record], sealedTypes: Set[String]): String = {
      addOptionRecords(records, sealedTypes)
        .map(r => grpcEncoder(r, sealedTypes))
        .mkString("\n\n")
    }
  }
}
