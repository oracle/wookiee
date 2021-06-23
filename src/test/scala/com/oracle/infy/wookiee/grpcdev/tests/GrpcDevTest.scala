package com.oracle.infy.wookiee.grpcdev.tests

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO
import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen.{genScala, genService, sealedTypes, toRecord}
import utest.{Tests, test}

import scala.reflect.runtime.universe.typeOf


object GrpcDevTest {

  final case class MockRequest(a: String)
  final case class MockReturn(a: String)
  val mockRPCs =  List(
    typeOf[IO[MockRequest, MockReturn]] -> "mockRpc"
  )

  val mockTypes = List(
    typeOf[MockRequest],
    typeOf[MockReturn]
  ).map(_.typeSymbol)

  val mockSealedTypeLookup = sealedTypes(mockTypes)

  val mockRecords = mockTypes.map(toRecord)

  def tests(): Tests = {

    val genServiceIsNonEmpty = {
      val service = genService(mockRPCs, mockRecords, mockSealedTypeLookup, "foo.bar", "mockFoo")
      service.trim.nonEmpty
    }

    val genScalaIsNonEmpty = {
      val scala = genScala(
        mockRecords,
        mockSealedTypeLookup,
      "foo"
      )
      scala.trim.nonEmpty
    }

    Tests {
      test("Gen Service returns a non empty string") {
        assert(genServiceIsNonEmpty)
      }
      test("Gen Scala returns a non empty string") {
        assert(genScalaIsNonEmpty)
      }

    }
  }
}
