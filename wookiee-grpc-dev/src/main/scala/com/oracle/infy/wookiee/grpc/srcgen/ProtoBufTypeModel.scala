package com.oracle.infy.wookiee.grpc.srcgen

object ProtoBufTypeModel {

  sealed trait ScalaRecord

  sealed trait ScalaType

  sealed trait PrimitiveScalaType extends ScalaType

  final case class Param(name: String, `type`: ScalaType)

  final case class CaseClass(className: String, params: List[Param])

  final case class ProtoField(name: String, tpe: String)
  final case class ProtoMessage(name: String, fields: List[ProtoField], oneOfs: List[ProtoField])

  implicit class ProtoMessageToString(lhs: ProtoMessage) {

    private def inner(fields: List[ProtoField], startingIndex: Int, nesting: Int): String =
      fields
        .zipWithIndex
        .map {
          case (field, index) =>
            s"${field.tpe} ${field.name} = ${index + 1 + startingIndex};"
        }
        .mkString("", s"\n${" " * nesting * 2}", "")

    def show: String = lhs match {
      case ProtoMessage(name, fields, Nil) =>
        s"""
           |message $name {
           |  ${inner(fields, 0, 1)}
           |}
           |""".stripMargin

      case ProtoMessage(name, fields, oneOfs) =>
        s"""
           |message $name {
           |  oneof OneOf {
           |    ${inner(oneOfs, 0, 2)}
           |  }
           |  
           |  ${inner(fields, fields.length, 1)}
           |}
           |""".stripMargin
    }
  }

  def main(args: Array[String]): Unit = {

    val result = ProtoMessage(
      "Person",
      List(
        ProtoField("name", "string"),
        ProtoField("age", "int32")
      ),
      List(
        ProtoField("left", "Left"),
        ProtoField("right", "Right")
      )
    ).show

    println("########")
    println(result)
  }
  // TODO
  // 1. Implement nested sealed traits
  // 2. Use types from "wrappers.proto" for Optional scalars (https://scalapb.github.io/docs/customizations#primitive-wrappers)
  // 3. Use named fields inside "oneof" (instead of a, b, c, etc)
  // 4. Ensure generated proto contains top level error
  /*
        message ASError {

          string foo = 1;

          oneof OneOf {
            ValidationError validationError = 1;
            InvalidInputError invalidInputError = 2;
          }

          message Person {
            string name = 1;
          }

          message ValidationError {}

          message InvalidInputError {}
        }
   */
  // 5. Add ability to import third-party proto file in generated proto file
  // 6. Add ability to define rpcs that use third-party message types
  // 7. Think about using annotations to exclude third-party types
  /*
      object Main {
        sealed trait ASError

        sealed trait DestinationError

        @provided(maxyError)
        case class ValidationError(code: Foo, maxyError: MaxymiserBaseError) extends ASError with DestinationError
      }
 */

}
