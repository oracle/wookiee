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


    Tests {
      test("Gen Service returns a non empty string") {
        assert(genServiceIsNonEmpty)
      }
      test("Gen Scala returns a non empty string") {
        assert(genScalaIsNonEmpty)
      }
//      test("Gen Scala returns a non empty string") {
//        assert(genScalaIsNonEmpty)
//      }

    }
  }
}
