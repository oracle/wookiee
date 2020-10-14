package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import scala.reflect.runtime.universe.typeOf

object GrpcSourceGen extends SrcGen {

  final case class SomeRequest(field: String)

  sealed trait SomeResponse
  final case class SuccessfulResponse() extends SomeResponse
  final case class FailureResponse(err: String) extends SomeResponse

  def main(args: Array[String]): Unit = {

    val types = List(
      typeOf[SomeRequest],
      typeOf[SomeResponse]
    ).map(_.typeSymbol)

    val sealedTypeLookup = sealedTypes(types)
    val records = types.map(toRecord)

    val rpcs = List(
      typeOf[IO[SomeRequest, SomeResponse]] -> "someRpc"
    )
    val protoSrc = genService(rpcs, records, sealedTypeLookup, "some.package", "SomeService")

    val scalaSrc = genScala(records, sealedTypeLookup, "import some.header")

    // Write these to a file
    println(protoSrc)
    println(scalaSrc)

    ()
  }
}
