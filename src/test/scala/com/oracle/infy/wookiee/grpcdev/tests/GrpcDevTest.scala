package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen.{genScala, genService}
import com.oracle.infy.wookiee.grpcdev.common._
import utest.{Tests, test}

object GrpcDevTest {

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val (mockRPCs, mockRecords, mockSealedTypeLookup) = MockObjectSets.EmptyMocks
      val service = genService(mockRPCs, mockRecords, mockSealedTypeLookup, "foo.bar", "mockFoo")
      println(service)
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val (_, mockRecords, mockSealedTypeLookup) = MockObjectSets.EmptyMocks
      val scala = genScala(
        mockRecords,
        mockSealedTypeLookup,
      "foo"
      )
      println(scala)
      scala.trim.nonEmpty
    }
    // For the following tests for : Service gen and Scala Gen
    // Use encoder/decoder functions
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
      test("Gen Service returns a non empty string") {
        assert(genServiceIsNonEmpty)
      }

      test("Gen Scala returns a non empty string") {
        assert(genScalaIsNonEmpty)
      }

      //
//      test("Gen Scala returns a non empty string") {
//        //TODO Parameterize tests
//        assert(testCodeGen(types, expectedvalues))
//      }

    }
  }
}
