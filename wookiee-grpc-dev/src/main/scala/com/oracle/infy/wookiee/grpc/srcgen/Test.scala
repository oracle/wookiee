package com.oracle.infy.wookiee.grpc.srcgen

import java.nio.file.Paths
import scala.meta._

object Test {

  sealed trait Result

  final case class Ok(value: String) extends Result
  final case class Err(value: Int) extends Result

  def main(args: Array[String]): Unit = {

    val path =
      "/Users/lachandr/Projects/wookiee/wookiee-grpc-dev/src/main/scala/com/oracle/infy/wookiee/grpc/srcgen/Example.scala"

    val src = new String(java.nio.file.Files.readAllBytes(Paths.get(path)))

    val x = List(
      Input.VirtualFile("Example.scala", src)
    ).map(_.parse[Source])
      .map(_.get)
      .flatMap { source =>
        source.collect {
          case node: Defn.Trait =>
            node
          case node: Defn.Class =>
            node
        }

      }

    println(x)

  }
}
