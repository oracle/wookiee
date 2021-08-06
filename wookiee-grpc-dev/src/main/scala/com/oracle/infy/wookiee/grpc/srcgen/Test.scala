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
        val defns = source.collect {
          case node: Defn.Trait =>
            node
          case node: Defn.Class =>
            node
        }

        val sealedTraitMap = calculateSealedTraits(defns)

        defns.map {
          case clazz: Defn.Class =>
            handleCaseClass(clazz)
          case value: Defn.Trait =>
            handleSealedTrait(value, sealedTraitMap)
          case _ => "not a valid type"
        }
      }
    // todo -- have a filter stage to filter out case classes that do not conform (type paramters, implicit params, etc)

    println(protoStrings.mkString("\n"))

  }

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childName: String,
      sealedTraitMap: Map[String, List[String]]
  ): Map[String, List[String]] = {
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          innerMap + (parentClass -> (currentMembers :+ childName))
        // todo -- decide how to handle these
        case _ =>
          innerMap
      }
    }
  }

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[String]] = {
    defns
      .foldLeft(Map.empty[String, List[String]]) { (map, node) =>
        node match {
          case clazz: Defn.Class =>
            addParentsToSealedTraitMap(clazz.templ.inits, clazz.name.value, map)
          case value: Defn.Trait =>
            addParentsToSealedTraitMap(value.templ.inits, value.name.value, map)
          case _ =>
            // todo -- is this an error?
            map
        }
      }
  }

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[String]]): String = {

    val prefix = s"""message ${value.name} {"""

    sealedTraitMap
      .get(value.name.value)
      .map(
        _.zipWithIndex
          .map {
            case (childNode, index) =>
              s"$childNode ${childNode.take(1).map(_.toLower) + childNode.drop(1)} = ${index + 1};"
          }
          .mkString(" ")
      )
      .map { oneOfs =>
        s"""oneof OneOf {
        $oneOfs 
          }         
         """
      }
      .map { oneOfString =>
        s"$prefix $oneOfString \n}"
      }
      .getOrElse(
        s"$prefix \n}"
      )

  }

  def handleCaseClass(clazz: Defn.Class): String = {

    val prefix = s"""message ${clazz.name} {"""

    // flatten implicit params and params because we filter out implicits
    val paramStrings = clazz
      .ctor
      .paramss
      .flatten
      .foldLeft((1, List.empty[String])) {
        case ((paramInt, protoFields), param) =>
          // todo -- decide what happens if decltpe is empty... throw an error? ignore the param? ???
          val maybeProtoField = param.decltpe.flatMap {
            case Type.Name(value) =>
              value match {
                case "Int"    => Some(s"int32 ${param.name.value} = $paramInt;")
                case "String" => Some(s"string ${param.name.value} = $paramInt;")
                // todo -- fill these in
                case "Option"  => None
                case "etc etc" => None
              }
            // we should not get into this case (where the type is not explicitly declared) after our filters
            case _ => None
          }

          maybeProtoField match {
            case Some(protoField) =>
              (paramInt + 1, protoFields :+ protoField)
            case None =>
              (paramInt, protoFields)
          }
      }
      ._2

    s"$prefix ${paramStrings.mkString(" ")} }"
  }
}
