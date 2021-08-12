package com.oracle.infy.wookiee.grpc.srcgentwo

import scala.meta.{Term, _}

object TestTwo {

  final case class Model(name: String, oneOfs: List[Term.Param], fields: List[Term.Param])

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
            s"    $paramType $paramStr = ${index + 1};"
        }
        .mkString("  oneof OneOf {\n", "\n", "\n  }")

    private def renderFields(fields: List[Term.Param], offset: Int): String = {

      def renderType(t: Type): String = t match {
        case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
          s"Maybe$innerType"
        case Type.Apply(Type.Name("Option"), head :: Nil) =>
          s"Maybe${renderType(head)}"
        case Type.Name("String")  => "string"
        case Type.Name("Int")     => "int32"
        case Type.Name(nonScalar) => nonScalar
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
        case Model(name, Nil, Nil) =>
          s"""
             |message $name {
             |}
             |""".stripMargin

        case Model(name, oneOfs, Nil) =>
          s"""
             |message $name {
             |${renderOneOfs(oneOfs)}
             |}
             |""".stripMargin

        case Model(name, Nil, fields) =>
          s"""
             |message $name {
             |${renderFields(fields, offset = 1)}
             |}
             |""".stripMargin

        case Model(name, oneOfs, fields) =>
          s"""
             |message $name {
             |${renderOneOfs(oneOfs)}
             |${renderFields(fields, offset = oneOfs.length)}
             |}
             |""".stripMargin

      }
  }

  def synthesizeOptionModel(input: List[Model]): Set[Model] = {

    def handleType(t: Type, acc: Set[Model]): Set[Model] = t match {
      case Type.Apply(Type.Name("Option"), Type.Name("String") :: Nil) =>
        acc ++ Set(
          Model(
            "MaybeString",
            oneOfs = List(
              Term.Param(mods = Nil, name = Term.Name("some"), decltpe = Some(Type.Name("string")), default = None),
              Term.Param(mods = Nil, name = Term.Name("none"), decltpe = Some(Type.Name("None")), default = None)
            ),
            fields = Nil
          ),
          Model(
            "None",
            oneOfs = Nil,
            fields = Nil
          )
        )
      case _ => acc
    }

    input
      .foldLeft(Set.empty[Model]) {
        case (acc, model) =>
          model
            .fields
            .flatMap(_.decltpe)
            .flatMap(t => handleType(t, acc))
            .toSet
      }
  }

  def main(args: Array[String]): Unit = {
    val model = Model(
      name = "Person",
      oneOfs = Nil,
      fields = List(
        Term.Param(
          Nil,
          Term.Name("name"),
          Some(Type.Apply(Type.Name("Option"), List(Type.Name("String")))),
//          Some(Type.Apply(Type.Name("Option"), List(Type.Apply(Type.Name("Option"), List(Type.Name("String")))))),
//          Some(
//            Type.Apply(
//              Type.Name("Option"),
//              List(
//                Type.Apply(
//                  Type.Name("Option"),
//                  List(
//                    Type.Apply(Type.Name("Option"), List(Type.Name("String")))
//                  )
//                )
//              )
//            )
//          ),
          None
        )
      )
    )

    val models = List(model)
    val protoContent = (models ++ synthesizeOptionModel(models)).map(_.renderProto).mkString("\n")
    println(protoContent)
  }

}
