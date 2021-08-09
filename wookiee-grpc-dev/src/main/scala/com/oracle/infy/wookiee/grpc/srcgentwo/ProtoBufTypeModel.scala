package com.oracle.infy.wookiee.grpc.srcgentwo

import org.scalafmt.interfaces.Scalafmt

import java.nio.file.Paths
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

    def scalaObjectToGrpc(protoMessage: ProtoMessage): String =
      protoMessage.scalametaObj match {
        case Left(clazz) =>
          val name = clazz.name.value
          val modelTerm = Term.Name(name)
          val modelType = Type.Name(name)

          val fromGrpcImplicitClassName = Type.Name(s"${name}FromGrpc")
          val toGrpcImplicitClassName = Type.Name(s"${name}ToGrpc")

          val grpcType = Type.Name(s"Grpc$name")
          val returnTypeTerm = Term.Name(s"Grpc$name")

          val fields = clazz
            .ctor
            .paramss
            .flatten
            .map { param =>
              val paramName = param.name.value

              val nonScalarAssign = Term.Assign(
                Term.Name(paramName),
                q"lhs.${Term.Name(paramName)}.toGrpc"
              )

              val scalarAssign = Term.Assign(Term.Name(paramName), Term.Select(Term.Name("lhs"), Term.Name(paramName)))

              param
                .decltpe
                .map {
                  // TODO: Handle other scalars
                  case Type.Name("String") | Type.Name("Int") =>
                    scalarAssign
                  case _ =>
                    nonScalarAssign
                }
                .getOrElse(nonScalarAssign)
            }

          val toGrpc =
            q"""
                implicit class $toGrpcImplicitClassName (lhs: $modelType) {
                  def toGrpc: $grpcType = {
                    $returnTypeTerm(..$fields)
                  }
                }
            """

          val typesnel = List(Type.Name("String"), modelType)

          val enumeratorsnel = clazz
            .ctor
            .paramss
            .flatten
            .map { param =>
              val paramName = param.name.value

              val t = Term.Select(Term.Name("lhs"), Term.Name(paramName))
              val app = q"Right($t)"
              val scalarGenerator = Enumerator.Generator(Pat.Var(Term.Name(paramName)), app)

              val nonScalarGenerator =
                Enumerator.Generator(Pat.Var(Term.Name(paramName)), q"lhs.${Term.Name(paramName)}.fromGrpc")

              param
                .decltpe
                .map {
                  case Type.Name("String") | Type.Name("Int") =>
                    scalarGenerator
                  case _ =>
                    nonScalarGenerator
                }
                .getOrElse(nonScalarGenerator)
            }

          val forYield = q"for(..$enumeratorsnel) yield $modelTerm(..$fields)"
          val fromGrpc =
            q"""
                implicit class $fromGrpcImplicitClassName (lhs: $grpcType) {
                  def fromGrpc: Either[..$typesnel] = {
                    $forYield
                  }
                }
            """

          val scalafmt = Scalafmt.create(this.getClass.getClassLoader)
          val code = s"$toGrpc \n $fromGrpc"
          scalafmt.format(Paths.get("/Users/lachandr/Projects/wookiee/.scalafmt.conf"), Paths.get("Main.scala"), code)

        case Right(value) =>
          value.toString()
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
