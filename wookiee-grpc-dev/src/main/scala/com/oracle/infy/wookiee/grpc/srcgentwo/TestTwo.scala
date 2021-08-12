package com.oracle.infy.wookiee.grpc.srcgentwo

import org.scalafmt.interfaces.Scalafmt

import java.nio.file.{Files, Paths}
import scala.meta.inputs.Input
import scala.meta.{Term, _}

object TestTwo {

  final case class Model(scalaTypeName: String, grpcTypeName: String, oneOfs: List[Term.Param], fields: List[Term.Param])

  implicit class Transpiler(lhs: Model) {

    private def renderOneOfs(oneOfs: List[Term.Param]): String =
      oneOfs
        .zipWithIndex
        .map {
          case (param, index) =>
            val paramStr = param.name.value
            val paramType = param.decltpe.getOrElse(Type.Name("Unknown")) match {
              case Type.Name(str) => str
              case _              => "Unknown"
            }
            s"    Grpc$paramType $paramStr = ${index + 1};"
        }
        .mkString("  oneof OneOf {\n", "\n", "\n  }")

    private def renderFields(fields: List[Term.Param], offset: Int): String = {

      def renderType(t: Type): String = t match {
        case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
          s"GrpcMaybe$innerType"
        case Type.Apply(Type.Name("Option"), head :: Nil) =>
          s"Maybe${renderType(head)}"
        case Type.Name("String")  => "string"
        case Type.Name("Int")     => "int32"
        case Type.Name(nonScalar) => s"Grpc$nonScalar"
        case _                    => "Unknown"
      }

      fields
        .zipWithIndex
        .map {
          case (param, index) =>
            val paramStr = param.name.value
            val paramType = renderType(param.decltpe.getOrElse(Type.Name("GrpcUnknown")))
            s"  $paramType $paramStr = ${index + offset};"
        }
        .mkString("\n")
    }

    def renderProto: String =
      lhs match {
        case Model(_, grpcTypeName, Nil, Nil) =>
          s"""
             |message $grpcTypeName {
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, oneOfs, Nil) =>
          s"""
             |message $grpcTypeName {
             |${renderOneOfs(oneOfs)}
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, Nil, fields) =>
          s"""
             |message $grpcTypeName{
             |${renderFields(fields, offset = 1)}
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, oneOfs, fields) =>
          s"""
             |message $grpcTypeName{
             |${renderOneOfs(oneOfs)}
             |${renderFields(fields, offset = oneOfs.length)}
             |}
             |""".stripMargin

      }
  }

  def synthesizeOptionModel(input: List[Model]): Set[Model] = {

    def handleScalarGrpcTypes(scalaType: String) =
      scalaType match {
        case "String" => "string"
        case "Int"    => "int32"
        case value    => value
      }

    def handleType(t: Type, acc: Set[Model]): (Set[Model], String) = t match {
      case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
        val newTypeName = "Maybe" + innerType

        (
          acc ++ Set(
            Model(
              newTypeName,
              s"Grpc$newTypeName",
              oneOfs = List(
                Term.Param(
                  mods = Nil,
                  name = Term.Name("some"),
                  decltpe = Some(Type.Name(handleScalarGrpcTypes(innerType))),
                  default = None
                ),
                Term.Param(mods = Nil, name = Term.Name("none"), decltpe = Some(Type.Name("None")), default = None)
              ),
              fields = Nil
            ),
            Model(
              "None",
              "GrpcNone",
              oneOfs = Nil,
              fields = Nil
            )
          ),
          newTypeName
        )
      case Type.Apply(Type.Name("Option"), head :: Nil) =>
        val (innerModel, innerTypeName): (Set[Model], String) = handleType(head, acc)
        val newTypeName = "Maybe" + innerTypeName

        (
          acc ++ Set(
            Model(
              newTypeName,
              s"Grpc$newTypeName",
              oneOfs = List(
                Term.Param(
                  mods = Nil,
                  name = Term.Name("some"),
                  decltpe = Some(Type.Name(innerTypeName)),
                  default = None
                ),
                Term.Param(mods = Nil, name = Term.Name("none"), decltpe = Some(Type.Name("None")), default = None)
              ),
              fields = Nil
            )
          ) ++ innerModel,
          newTypeName
        )

      case _ => (acc, "")
    }

    input
      .foldLeft(Set.empty[Model]) {
        case (acc, model) =>
          model
            .fields
            .flatMap(_.decltpe)
            .flatMap(t => handleType(t, acc)._1)
            .toSet
      }
  }

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childTypeName: String,
      sealedTraitMap: Map[String, List[Term.Param]]
  ): Map[String, List[Term.Param]] =
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          //val prefixedChildTypeName = s"$Grpc$childTypeName"
          innerMap + (parentClass -> (currentMembers :+
            Term.Param(
              Nil,
              Term.Name(childTypeName.take(1).toLowerCase + childTypeName.drop(1)),
              Some(Type.Name(childTypeName)),
              None
            )))
        // todo -- decide how to handle these
        case _ =>
          innerMap
      }
    }

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[Term.Param]] =
    defns
      .foldLeft(Map.empty[String, List[Term.Param]]) { (map, node) =>
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

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[Term.Param]]): Model =
    Model(value.name.value, s"Grpc${value.name.value}", sealedTraitMap.getOrElse(value.name.value, List.empty), Nil)

  def handleCaseClass(clazz: Defn.Class): Model = {
    // flatten implicit params and params because we filter out implicits
    val protoFields = clazz
      .ctor
      .paramss
      .flatten

    Model(clazz.name.value, s"Grpc${clazz.name.value}", Nil, protoFields)
  }

  def main(args: Array[String]): Unit = {

//    val caseClassModel = Model(
//      name = "Person",
//      oneOfs = Nil,
//      fields = List(
//        Term.Param(
//          Nil,
//          Term.Name("name"),
//          //Some(Type.Apply(Type.Name("Option"), List(Type.Name("String")))),
//          Some(Type.Apply(Type.Name("Option"), List(Type.Apply(Type.Name("Option"), List(Type.Name("Int")))))),
////          Some(
////            Type.Apply(
////              Type.Name("Option"),
////              List(
////                Type.Apply(
////                  Type.Name("Option"),
////                  List(
////                    Type.Apply(Type.Name("Option"), List(Type.Name("String")))
////                  )
////                )
////              )
////            )
////          ),
//          None
//        )
//      )
//    )
//
//    val traitModel = Model(
//      name = "ParentTrait",
//      oneOfs = List(Term.Param(Nil, Term.Name("fieldName"), Some(Type.Name("FieldType")), None)),
//      fields = Nil
//    )

//    val models = List(caseClassModel, traitModel)

    val path = "wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/Example.scala"

    val src = new String(java.nio.file.Files.readAllBytes(Paths.get(path)))
    val models = List(
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

    println("--------- Proto Messages ---------")

    val generatedProto = (models ++ synthesizeOptionModel(models)).map(_.renderProto).mkString("\n")

    val protoContent = List(
      """syntax = "proto3";""",
      "package com.oracle.infy.wookiee.grpc.srcgen.testService;",
      generatedProto,
      """
        |service TestService {
        |  rpc test(GrpcPerson) returns (GrpcPerson) {}
        |}
        |""".stripMargin
    ).mkString("\n")
    println(protoContent)

    Files.write(Paths.get("wookiee-proto/src/main/protobuf/testService.proto"), protoContent.getBytes)

    println("--------- ScalaCode ---------")

    val scalafmt: Scalafmt = Scalafmt.create(this.getClass.getClassLoader)
    val fmt: String => String = str => scalafmt.format(Paths.get(".scalafmt.conf"), Paths.get("Main.scala"), str)

    val generatedScala = models.map(model => TestTwoRenderScala.renderScala(model, fmt)).mkString("\n")

    val scalaContent =
      s"""
      package com.oracle.infy.wookiee.srcgen
      import Example._
      import com.oracle.infy.wookiee.grpc.srcgen.testService.testService._

      object implicits {
        ${fmt(generatedScala)}
      }
    """.stripMargin


    println(scalaContent)

    Files.write(
      Paths.get("wookiee-proto/src/main/scala/com/oracle/infy/wookiee/srcgen/implicits.scala"),
      fmt(scalaContent).getBytes
    )

    ()
  }

}
