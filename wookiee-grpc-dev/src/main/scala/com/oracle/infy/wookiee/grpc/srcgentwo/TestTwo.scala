package com.oracle.infy.wookiee.grpc.srcgentwo

import org.scalafmt.interfaces.Scalafmt

import java.nio.file.{Files, Paths}
import scala.meta.inputs.Input
import scala.meta.{Term, _}

object TestTwo {

  final case class ParamModel(param: Term.Param, grpcType: String)

  final case class Model(
      scalaTypeName: String,
      grpcTypeName: String,
      oneOfs: List[ParamModel],
      fields: List[ParamModel]
  )

  implicit class Transpiler(lhs: Model) {

    private def renderOneOfs(oneOfs: List[ParamModel]): String =
      oneOfs
        .zipWithIndex
        .map {
          case (paramModel, index) =>
            val paramName = paramModel.param.name.value
            val paramGrpcType = paramModel.grpcType
            s"    $paramGrpcType $paramName = ${index + 1};"
        }
        .mkString("  oneof OneOf {\n", "\n", "\n  }")

    private def renderFields(fields: List[ParamModel], offset: Int): String =
      fields
        .zipWithIndex
        .map {
          case (paramModel, index) =>
            val paramName = paramModel.param.name.value
            val paramGrpcType = paramModel.grpcType

            s"  $paramGrpcType $paramName = ${index + offset};"
        }
        .mkString("\n")

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

  def getOptionalTypes(input: List[Model]): Set[Type] = {

    def expand(t: Type.Apply): List[Type] =
      t match {
        case Type.Apply(Type.Name("Option"), Type.Name(_) :: Nil) => List(t)
        case Type.Apply(Type.Name("Option"), (app @ Type.Apply(_, _)) :: Nil) =>
          t :: expand(app)
        case _ => Nil
      }

    input
      .flatMap(_.fields)
      .flatMap(_.param.decltpe)
      .collect {
        case t @ Type.Apply(Type.Name("Option"), _) => t
      }
      .flatMap(expand)
      .groupBy(_.toString())
      .view
      .mapValues(_.headOption)
      .values
      .flatten
      .toSet
  }

  def synthesizeOptionModel(input: List[Model]): Set[Model] = {

    final case class HandleTypeReturn(models: Set[Model], grpcType: String)

    def handleType(t: Type, acc: Set[Model]): HandleTypeReturn = {
      val noneType = Type.Name("None")

      t match {
        case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
          val newTypeName = "Maybe" + innerType

          HandleTypeReturn(
            acc ++ Set(
              Model(
                // Using scala type for both because at the end scala type is converted to grpc type
                // If we call grpcType(newTypeName), we end up with types like "GrpcMaybeGrpcMaybe"
                newTypeName,
                newTypeName,
                oneOfs = List(
                  ParamModel(
                    Term.Param(
                      mods = Nil,
                      name = Term.Name("some"),
                      decltpe = Some(Type.Name(innerType)),
                      default = None
                    ),
                    innerType
                  ),
                  ParamModel(
                    Term.Param(mods = Nil, name = Term.Name("none"), decltpe = Some(noneType), default = None),
                    "None"
                  )
                ),
                fields = Nil
              )
            ),
            newTypeName
          )
        case Type.Apply(Type.Name("Option"), head :: Nil) =>
          val HandleTypeReturn(innerModel, innerTypeName) = handleType(head, acc)
          val newTypeName = "Maybe" + innerTypeName

          HandleTypeReturn(
            acc ++ Set(
              Model(
                // Using scala type for both because at the end scala type is converted to grpc type
                // If we call grpcType(newTypeName), we end up with types like "GrpcMaybeGrpcMaybe"
                newTypeName,
                newTypeName,
                oneOfs = List(
                  ParamModel(
                    Term.Param(
                      mods = Nil,
                      name = Term.Name("some"),
                      decltpe = Some(Type.Name(innerTypeName)),
                      default = None
                    ),
                    innerTypeName
                  ),
                  ParamModel(
                    Term.Param(mods = Nil, name = Term.Name("none"), decltpe = Some(noneType), default = None),
                    "None"
                  )
                ),
                fields = Nil
              )
            ) ++ innerModel,
            newTypeName
          )

        case _ => HandleTypeReturn(acc, "")
      }
    }

    val expandedOptions = input
      .foldLeft(Set.empty[Model]) {
        case (acc, model) =>
          model
            .fields
            .flatMap(_.param.decltpe)
            .flatMap(t => handleType(t, acc).models)
            .toSet
      }

    if (expandedOptions.nonEmpty) {
      // Having it as a set didn't auto dedupe
      val dedupedExpandedOptions = expandedOptions
        .groupBy(_.grpcTypeName)
        .view
        .mapValues(_.headOption)
        .values
        .flatten
        .toSet
        .map { model: Model =>
          model.copy(
            grpcTypeName = getGrpcType(Some(Type.Name(model.scalaTypeName))),
            oneOfs = model.oneOfs.map { paramModel =>
              paramModel.copy(grpcType = getGrpcType(Some(Type.Name(paramModel.grpcType))))
            }
          )
        }

      (dedupedExpandedOptions + Model(
        scalaTypeName = "None",
        grpcTypeName = getGrpcType(Some(Type.Name("None"))),
        oneOfs = Nil,
        fields = Nil
      ))

    } else {
      expandedOptions
    }

  }

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childTypeName: String,
      sealedTraitMap: Map[String, List[ParamModel]]
  ): Map[String, List[ParamModel]] =
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          val childType = Type.Name(childTypeName)
          innerMap + (parentClass -> (currentMembers :+
            ParamModel(
              Term.Param(
                Nil,
                Term.Name(childTypeName.take(1).toLowerCase + childTypeName.drop(1)),
                Some(childType),
                None
              ),
              getGrpcType(Some(childType))
            )))
        // todo -- decide how to handle these
        case _ =>
          innerMap
      }
    }

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[ParamModel]] =
    defns
      .foldLeft(Map.empty[String, List[ParamModel]]) { (map, node) =>
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

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[ParamModel]]): Model =
    Model(
      value.name.value,
      getGrpcType(Some(Type.Name(value.name.value))),
      sealedTraitMap.getOrElse(value.name.value, List.empty),
      Nil
    )

  def handleCaseClass(clazz: Defn.Class): Model = {
    // flatten implicit params and params because we filter out implicits
    val protoFields = clazz
      .ctor
      .paramss
      .flatten
      .map { param =>
        ParamModel(param, getGrpcType(param.decltpe))
      }

    Model(clazz.name.value, getGrpcType(Some(Type.Name(clazz.name.value))), Nil, protoFields)
  }

  def getGrpcScalarType: PartialFunction[Type, String] = {
    case Type.Name("String")  => "string"
    case Type.Name("Int")     => "int32"
    case Type.Name("Boolean") => "bool"
  }

  def isScalarType(t: Type): Boolean =
    getGrpcScalarType
      .andThen(_ => true)
      .orElse[Type, Boolean] { _ =>
        false
      }(t)

  def getGrpcType(t: Option[Type]): String = {

    val Grpc = "Grpc"

    def getGrpcMessageType: PartialFunction[Type, String] = {
      case Type.Name(nonScalar) => s"$Grpc$nonScalar"
    }

    def getGrpcScalarType: PartialFunction[Type, String] = {
      case Type.Name("String")  => "string"
      case Type.Name("Int")     => "int32"
      case Type.Name("Boolean") => "bool"
    }

    def getGrpcOptionType: PartialFunction[Type, String] = {
      case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
        s"Maybe$innerType"

      case Type.Apply(Type.Name("Option"), head :: Nil) =>
        s"Maybe${getGrpcOptionType(head)}"

    }

    getGrpcOptionType
      .andThen(ot => s"$Grpc$ot")
      .orElse(getGrpcScalarType)
      .orElse(getGrpcMessageType)
      .orElse[Type, String] {
        case _ => "Unknown"
      }(t.getOrElse(Type.Name("Unknown")))
  }

  def main(args: Array[String]): Unit = {

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

    val generatedScala = {
      (models
        .map(model => TestTwoRenderScala.renderScala(model, fmt)) :+ (
        getOptionalTypes(models).map(a => TestTwoRenderScala.renderScalaOptional(a, fmt)).mkString("\n")
      )).mkString("\n")
    }

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
