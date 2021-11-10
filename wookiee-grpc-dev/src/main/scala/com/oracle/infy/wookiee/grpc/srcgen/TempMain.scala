package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.SourceGen.{RPC, RPCType, Service}
import com.oracle.infy.wookiee.grpc.srcgen.SourceGenModel.ScalaFileSource

import java.nio.file.{Files, Paths}

object TempMain extends SourceGen {

  val sources = List(
    ScalaFileSource("wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/Example3.scala"),
    ScalaFileSource("wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/Example3Extra.scala")
  )

  def main(args: Array[String]): Unit = {

    val protoOutputPath = Paths.get("wookiee-proto/src/main/protobuf/myService3.proto")

    val protoContent = genProto(
      headers = List(
        "package com.oracle.infy.grpc.activity.generated;"
      ),
      services = List(
        Service(
          name = "Activity",
          rpcs = List(
            RPC(
              "test",
              RPCType("GrpcValidationException", isStreaming = true),
              RPCType("GrpcInvalidGroupingIntervalException", isStreaming = false)
            )
          )
        )
      ),
      sources = sources
    )

    Files.write(protoOutputPath, protoContent.getBytes)
    ()
  }
}
