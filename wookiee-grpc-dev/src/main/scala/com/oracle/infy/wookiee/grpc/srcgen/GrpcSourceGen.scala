package com.oracle.infy.wookiee.grpc.srcgen

import java.nio.file.{Files, Paths}

import com.oracle.infy.wookiee.grpc.srcgen.Model.IO

import scala.reflect.runtime.universe.typeOf

object GrpcSourceGen extends SrcGen {

  final case class SomeRequest(field: String)

  sealed trait SomeResponse
  final case class SuccessfulResponse() extends SomeResponse
  final case class FailureResponse(err: String) extends SomeResponse

  final case class RequestWithOption(
                                      field: Option[SomeRequest],
                                      listField: Option[List[String]]
                                    )

  def main(args: Array[String]): Unit = {

    val types = List(
      typeOf[SomeRequest],
      typeOf[SomeResponse],
      typeOf[RequestWithOption]
    ).map(_.typeSymbol)

    val sealedTypeLookup = sealedTypes(types)
    val records = types.map(toRecord)

    val rpcs = List(
      typeOf[IO[SomeRequest, SomeResponse]] -> "someRpc"
    )
    val protoSrc = genService(rpcs, records, sealedTypeLookup, "some.package", "SomeService")

    val scalaSrc = genScala(records, sealedTypeLookup, "import some.header")


    Files.write(Paths.get("/Users/msiegfri/work/wookiee/deleteMe.proto"), protoSrc.getBytes("UTF8"))
    Files.write(Paths.get("/Users/msiegfri/work/wookiee/deleteMe2.scala"), scalaSrc.getBytes("UTF8"))

    // Write these to a file
    println(protoSrc)
    println(scalaSrc)

    ()
  }
}
