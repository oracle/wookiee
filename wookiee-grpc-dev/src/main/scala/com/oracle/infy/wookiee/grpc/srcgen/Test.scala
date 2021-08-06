package com.oracle.infy.wookiee.grpc.srcgen

import java.nio.file.Paths
import scala.meta._

object Test {

  sealed trait Result

  final case class Ok(value: String) extends Result
  final case class Err(value: Int) extends Result

  def main(args: Array[String]): Unit = {

    val path =
      "/Users/msiegfri/work/wookiee/wookiee-grpc-dev/src/main/scala/com/oracle/infy/wookiee/grpc/srcgen/Example.scala"
      //"/Users/lachandr/Projects/wookiee/wookiee-grpc-dev/src/main/scala/com/oracle/infy/wookiee/grpc/srcgen/Example.scala"

    val src = new String(java.nio.file.Files.readAllBytes(Paths.get(path)))

    val protoStrings = List(
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
      // todo -- have a filter stage to filter out case classes that do not conform (type paramters, implicit params, etc)
      .map {
        case clazz: Defn.Class =>
          handleCaseClass(clazz)
        case value: Defn.Trait =>
        case _                 =>
      }

    println(protoStrings)

  }

  def handleCaseClass(clazz: Defn.Class): String = {

    val prefix = s"""message ${clazz.name} {"""

    // flatten implicit params and params because we filter out implicits
    val paramStrings = clazz.ctor.paramss.flatten.foldLeft((1, List.empty[String])) {
      case ((paramInt, protoFields), param) =>
        // todo -- decide what happens if decltpe is empty... throw an error? ignore the param? ???
        val maybeProtoField = param.decltpe.flatMap {
          case Type.Name(value) =>
            value match {
              case "Int" => Some(s"int32 ${param.name.value} = $paramInt;")
              // todo -- fill these in
              case "String"  => None
              case "Option"  => None
              case "etc etc" => None
            }
          // we should not get into this case (where the type is not explicitly declared) after our filters
          case _ => None
        }

        maybeProtoField match {
          case Some(protoField) =>
            (paramInt + 1, protoField :: protoFields)
          case None =>
            (paramInt, protoFields)
        }

    }

    // todo -- build this by mkString on paramStrings
    val middle = paramStrings._2

    val suffix = s"""}"""

    s"$prefix $middle $suffix"
  }
}
