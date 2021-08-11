package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.ProtoBufTypeModel.{ProtoField, ProtoMessage}
import org.scalafmt.interfaces.Scalafmt

import java.nio.file.{Files, Paths}
import scala.meta.inputs.Input
import scala.meta._

object Test {

  sealed trait Result

  final case class Ok(value: String) extends Result
  final case class Err(value: Int) extends Result

  def main(args: Array[String]): Unit = {

    val path = "wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/Example.scala"

    val src = new String(java.nio.file.Files.readAllBytes(Paths.get(path)))

    def optionTypeToProtoMessage(tpe: Type): Option[(Term.Name, Tree)] = {

      def expandOption(t: String, level: Int): (Term.Name, Tree) = {
        val maybeCount = "Maybe" * level
        val sealedTraitTerm = Term.Name(s"$maybeCount$t")
        val someTerm = Term.Name(s"Some$t")

        val tree = q"""
             sealed trait $sealedTraitTerm
             final case class $someTerm(value: $t) extends $sealedTraitTerm
             final case class None() extends $sealedTraitTerm
         """

        //
        sealedTraitTerm -> tree
      }

      tpe match {
        case Type.Apply(Type.Name("Option"), List(Type.Name("String"))) => Some(expandOption("String", 1))
        case _                                                          => None
      }
    }

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

    val protoContent = List(
      """syntax = "proto3";""",
      "package com.oracle.infy.wookiee.grpc.srcgen.testService;",
      protoMessages.map(_.renderProto).mkString(""),
      """
        |service TestService {
        |  rpc test(GrpcPerson) returns (GrpcPerson) {}
        |}
        |""".stripMargin
    ).mkString("\n")

    Files.write(Paths.get("wookiee-proto/src/main/protobuf/testService.proto"), protoContent.getBytes)

    println("--------- ScalaCode ---------")

    val scalafmt: Scalafmt = Scalafmt.create(this.getClass.getClassLoader)
    val fmt: String => String = str => scalafmt.format(Paths.get(".scalafmt.conf"), Paths.get("Main.scala"), str)

    val scalaContent =
      s"""
      package com.oracle.infy.wookiee.srcgen
      import Example._
      import com.oracle.infy.wookiee.grpc.srcgen.testService.testService._
      
      object implicits {
        ${protoMessages.map(_.renderImplicits(fmt)).mkString("\n")}
      }
    """.stripMargin

    Files.write(
      Paths.get("wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/implicits.scala"),
      fmt(scalaContent).getBytes
    )
    ()

  }

  // map<key_type, value_type>
  // key_type = Only scalars except float, double, or bytes
  // value_type = any type except another map
  // map field cannot be repeated
  // value_type cannot be repeated

  // cannot nest

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childTypeName: String,
      sealedTraitMap: Map[String, List[ProtoField]]
  ): Map[String, List[ProtoField]] =
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          val prefixedChildTypeName = s"$Grpc$childTypeName"
          innerMap + (parentClass -> (currentMembers :+ ProtoField(
            protobufFieldName = childTypeName.take(1).toLowerCase + childTypeName.drop(1),
            protobufType = prefixedChildTypeName,
            scalaType = childTypeName
          )))
        // todo -- decide how to handle these
        case _ =>
          innerMap
      }
    }

  private val Grpc = "Grpc"

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[ProtoField]] =
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

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[ProtoField]]): ProtoMessage =
    ProtoMessage(s"$Grpc${value.name.value}", Nil, sealedTraitMap.getOrElse(value.name.value, List.empty), Right(value))

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
              case "Int"    => Some(ProtoField(param.name.value, "int32", value))
              case "String" => Some(ProtoField(param.name.value, "string", value))
              // todo -- Handle other scalars like bool, float etc.
              case _ =>
                Some(ProtoField(protobufFieldName = param.name.value, protobufType = s"$Grpc$value", scalaType = value))
            }
          // we should not get into this case (where the type is not explicitly declared) after our filters
          case _ => None
        }
      }

    ProtoMessage(s"$Grpc${clazz.name.value}", protoFields, List.empty, Left(clazz))
  }
}
