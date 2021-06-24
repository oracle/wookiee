package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgen.Model.{CaseClass, Record, SealedTrait}
import com.oracle.infy.wookiee.grpc.srcgen.SrcGen
import com.oracle.infy.wookiee.grpcdev.common._
import com.oracle.infy.wookiee.utils.implicits._
import utest.{Tests, test}

object GrpcDevTest {

  object srcGenTestObject extends SrcGen {

    override def toProto(record: Record): String = {
      super.toProto(record)
    }
  }

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

    val toProtoOptionString = {
      val optionStringRecord = SealedTrait(
        "OptionString",
        "Option[String]",
        List(
          CaseClass("NoneString", "Option[String]", List()),
          CaseClass("SomeString", "Option[String]", List(("value", "String")))
        )
      )
      println(srcGenTestObject.toProto(optionStringRecord))
      val result = srcGenTestObject.toProto(optionStringRecord)
      val expectedProto =
        "// DO NOT EDIT! (this code is generated)\nmessage GrpcOptionString {\n  oneof sealed_value {\n    GrpcNoneString a = 1;\n    GrpcSomeString b = 2;\n  }\n}\n\n// DO NOT EDIT! (this code is generated)\nmessage GrpcNoneString {\n\n}\n\n// DO NOT EDIT! (this code is generated)\nmessage GrpcSomeString {\n  string value = 1;\n}"
      result.trim() === expectedProto
    }

    // For the following tests for : toProto, grpcEncoder, grpcDecoder
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

      test("toProto encodes Option[String] properly") {
        assert(toProtoOptionString)
      }

//      test("Gen Scala returns a non empty string") {
//        //TODO Parameterize tests
//        assert(testCodeGen(types, expectedvalues))
//      }

    }
  }
}
