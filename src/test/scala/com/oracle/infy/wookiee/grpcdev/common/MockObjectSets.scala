package com.oracle.infy.wookiee.grpcdev.common

import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen.{sealedTypes, toRecord}
import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import scala.reflect.runtime.universe.typeOf

final case class MockEmptyRequest()
final case class MockRequest(request: MockObject)

final case class MockEmptyReturn()

sealed trait MockMaybeResponse
final case class MockMaybeResponseCreated(createdObject: MockObject) extends MockMaybeResponse
final case class MockMaybeResponseFailed(code: Int, msg: String, detail: String) extends MockMaybeResponse
final case class MockMaybeResponseInvalid(errors: List[String]) extends MockMaybeResponse

final case class MockObject(foo: String)

object MockObjectSets {

  val EmptyMocks = {
    val mockRPCs = List(
      typeOf[IO[MockEmptyRequest, MockEmptyReturn]] -> "mockRpc"
    )

    val mockTypes = List(
      typeOf[MockEmptyRequest],
      typeOf[MockEmptyReturn]
    ).map(_.typeSymbol)

    val mockSealedTypeLookup = sealedTypes(mockTypes)

    val mockRecords = mockTypes.map(toRecord)

    (mockRPCs, mockRecords, mockSealedTypeLookup)
  }

  val SealedTraitMocks = {
    val mockRPCs = List(
      typeOf[IO[MockRequest, MockMaybeResponse]] -> "mockRpc"
    )

    val mockTypes = List(
      typeOf[MockRequest],
      typeOf[MockMaybeResponse],
      typeOf[MockMaybeResponseCreated],
      typeOf[MockMaybeResponseFailed],
      typeOf[MockMaybeResponseInvalid],
    ).map(_.typeSymbol)

    val mockSealedTypeLookup = sealedTypes(mockTypes)

    val mockRecords = mockTypes.map(toRecord)

    (mockRPCs, mockRecords, mockSealedTypeLookup)
  }
}



