package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.Example.ValidationError

import scala.meta._

object ProtoBufTypeModel {

  final case class ProtoField(protobufFieldName: String, protobufType: String)

  final case class ProtoMessage(
      protobufMessageName: String,
      fields: List[ProtoField],
      oneOfs: List[ProtoField],
      scalametaObj: Either[Defn.Class, Defn.Trait]
  )

  implicit class ProtoMessageToString(lhs: ProtoMessage) {

    private def inner(fields: List[ProtoField], startingIndex: Int, nesting: Int): String =
      fields
        .zipWithIndex
        .map {
          case (field, index) =>
            s"${field.protobufType} ${field.protobufFieldName} = ${index + 1 + startingIndex};"
        }
        .mkString("", s"\n${" " * nesting * 2}", "")

    def renderProto: String = lhs match {
      case ProtoMessage(name, fields, Nil, _) =>
        s"""
           |message $name {
           |  ${inner(fields, 0, 1)}
           |}
           |""".stripMargin

      case ProtoMessage(name, fields, oneOfs, _) =>
        s"""
           |message $name {
           |  oneof OneOf {
           |    ${inner(oneOfs, 0, 2)}
           |  }
           |  ${inner(fields, fields.length, 1)}
           |}
           |""".stripMargin
    }

//    implicit class ObjectReferenceToGrpc(lhs: ObjectReference) {
//      def toGrpc: GrpcObjectReference = {
//        GrpcObjectReference(
//          includeRelated = lhs.includeRelated,
//          principalReference = lhs.principalReference.toGrpc,
//          guid = lhs.guid
//        )
//      }
//    }
//
//    implicit class ObjectReferenceToADR(lhs: GrpcObjectReference) {
//      def toADR: Either[GrpcConversionError, ObjectReference] = {
//        for {
//          includeRelated <- Right(lhs.includeRelated)
//          principalReference <- lhs.principalReference.toADR
//          guid <- Right(lhs.guid)
//        } yield ObjectReference(includeRelated = includeRelated, principalReference = principalReference, guid = guid)
//      }
//    }

    def scalaObjectToGrpc(protoMessage: ProtoMessage): String = {

      protoMessage.scalametaObj match {
        case Left(clazz) =>
          val methodName = Type.Name(s"${clazz.name}ToGrpc")
          val className = clazz.name

          val x =
            q"""implicit class $methodName (lhs: $className) {
                  def toGrpc: Grpc${className} = {
                    Grpc$className(
                      //todo -- fill in fields
                    )
                  }
                }"""

          x.toString()
        case Right(value) =>
          value.toString()
      }

    }

    def renderImplicits: String = scalaObjectToGrpc(lhs)

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
