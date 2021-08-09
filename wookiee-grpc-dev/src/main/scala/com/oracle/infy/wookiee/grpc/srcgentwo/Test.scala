package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.ProtoBufTypeModel.{ProtoField, ProtoMessage}

import java.nio.file.Paths
import scala.meta.inputs.Input
import scala.meta.{Defn, Init, Source, Type}

object Test {

  sealed trait Result

  final case class Ok(value: String) extends Result
  final case class Err(value: Int) extends Result

  def main(args: Array[String]): Unit = {

    val path =
      "/Users/msiegfri/work/wookiee/wookiee-grpc-dev/src/main/scala/com/oracle/infy/wookiee/grpc/srcgentwo/Example.scala"
    //"/Users/lachandr/Projects/wookiee/wookiee-grpc-dev/src/main/scala/com/oracle/infy/wookiee/grpc/srcgentwo/Example.scala"

    val src = new String(java.nio.file.Files.readAllBytes(Paths.get(path)))

    val protoMessages = List(
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

        defns.flatMap {
          case clazz: Defn.Class =>
            Some(handleCaseClass(clazz))
          case value: Defn.Trait =>
            Some(handleSealedTrait(value, sealedTraitMap))
          case _ => None // not a valid type
        }
      }
    // todo -- have a filter stage to filter out case classes that do not conform (type paramters, implicit params, etc)


    println("--------- Proto Messages ---------")
    println(protoMessages.map(_.renderProto).mkString(""))
    println("--------- ScalaCode ---------")
    println(protoMessages.map(_.renderImplicits).mkString("\n"))

  }

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childName: String,
      sealedTraitMap: Map[String, List[ProtoField]]
  ): Map[String, List[ProtoField]] = {
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          innerMap + (parentClass -> (currentMembers :+ ProtoField(
            childName,
            childName.take(1).toUpperCase + childName.drop(1)
          )))
        // todo -- decide how to handle these
        case _ =>
          innerMap
      }
    }
  }

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[ProtoField]] = {
    defns
      .foldLeft(Map.empty[String, List[ProtoField]]) { (map, node) =>
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

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[ProtoField]]): ProtoMessage = {
    ProtoMessage(value.name.value, Nil, sealedTraitMap.getOrElse(value.name.value, List.empty), Right(value))
  }

  def handleCaseClass(clazz: Defn.Class): ProtoMessage = {
    // flatten implicit params and params because we filter out implicits
    val protoFields = clazz
      .ctor
      .paramss
      .flatten
      .flatMap { param =>
        // todo -- decide what happens if decltpe is empty... throw an error? ignore the param? ???
        param.decltpe.flatMap {
          case Type.Name(value) =>
            value match {
              case "Int"    => Some(ProtoField(param.name.value, "int32"))
              case "String" => Some(ProtoField(param.name.value, "string"))
              // todo -- fill these in
              case "Option"  => None
              case "etc etc" => None
            }
          // we should not get into this case (where the type is not explicitly declared) after our filters
          case _ => None
        }
      }

    ProtoMessage(clazz.name.value, protoFields, List.empty, Left(clazz))
  }
}
