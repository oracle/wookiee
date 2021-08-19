package com.oracle.infy.wookiee.grpc.srcgentwo

import scala.meta._

object ProtoBufTypeModel {

  final case class ProtoField(protobufFieldName: String, protobufType: String, scalaType: String)

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

      case ProtoMessage(name, Nil, oneOfs, _) =>
        s"""
           |message $name {
           |  oneof OneOf {
           |    ${inner(oneOfs, 0, 2)}
           |  }
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

    def scalaObjectToGrpc(
        protoMessage: ProtoMessage,
        fmt: String => String
    ): String =
      protoMessage.scalametaObj match {
        case Left(clazz) =>
          val name = clazz.name.value
          val modelTerm = Term.Name(name)
          val modelType = Type.Name(name)

          val fromGrpcImplicitClassName = Type.Name(s"${name}FromGrpc")
          val toGrpcImplicitClassName = Type.Name(s"${name}ToGrpc")

          val grpcType = Type.Name(protoMessage.protobufMessageName)
          val returnTypeTerm = Term.Name(protoMessage.protobufMessageName)

          val fields = clazz
            .ctor
            .paramss
            .flatten
            .map { param =>
              val paramName = param.name.value

              val nonScalarAssign = Term.Assign(
                Term.Name(paramName),
                q"Some(lhs.${Term.Name(paramName)}.toGrpc)"
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
              val upperCaseParamName = paramName.take(1).toUpperCase + paramName.drop(1)

              val t = Term.Select(Term.Name("lhs"), Term.Name(paramName))
              val app = q"Right($t)"
              val scalarGenerator = Enumerator.Generator(Pat.Var(Term.Name(paramName)), app)

              val nonScalarGenerator =
                Enumerator.Generator(
                  Pat.Var(Term.Name(paramName)),
                  q"lhs.${Term.Name(s"get$upperCaseParamName")}.fromGrpc"
                )

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

          val forAssign = clazz
            .ctor
            .paramss
            .flatten
            .map { param =>
              val p = Term.Name(param.name.value)
              Term.Assign(p, p)
            }

          val forYield = q"for(..$enumeratorsnel) yield $modelTerm(..$forAssign)"
          val fromGrpc =
            q"""
                implicit class $fromGrpcImplicitClassName (lhs: $grpcType) {
                  def fromGrpc: Either[..$typesnel] = {
                    $forYield
                  }
                }
            """

          val code = s"$toGrpc \n $fromGrpc"
          fmt(code)

        case Right(trt) =>
          val name = trt.name.value
//          val modelTerm = Term.Name(name)
          val modelType = Type.Name(name)

          val fromGrpcImplicitClassName = Type.Name(s"${name}FromGrpc")
          val toGrpcImplicitClassName = Type.Name(s"${name}ToGrpc")

          val grpcType = Type.Name(protoMessage.protobufMessageName)
          val grpcTerm = Term.Name(protoMessage.protobufMessageName)
//          val returnTypeTerm = Term.Name(protoMessage.protobufMessageName)

          val lhsMatch = Term.Match(
            Term.Name("lhs"),
            protoMessage
              .oneOfs
              .map { protoField =>
                val childTerm = Term.Name(protoField.scalaType)
                Case(
                  pat = Pat.Typed(Pat.Var(Term.Name("value")), Type.Name(protoField.scalaType)),
                  cond = None,
                  body = q"$grpcTerm($grpcTerm.OneOf.$childTerm(value.toGrpc))"
                )
              } :+
              Case(
                pat = Pat.Wildcard(),
                cond = None,
                body = q"$grpcTerm($grpcTerm.OneOf.Empty)"
              )
          )
          val toGrpcTree = q"""
             implicit class $toGrpcImplicitClassName(lhs: $modelType) {
               def toGrpc: $grpcType = $lhsMatch
             }
           """

          val typesnel = List(Type.Name("GrpcConversionError"), modelType)

          val matchStatement =
            Term.Match(
              Term.Select(Term.Name("lhs"), Term.Name("oneOf")),
              Case(
                pat = Term.Select(Term.Select(grpcTerm, Term.Name("OneOf")), Term.Name("Empty")),
                cond = None,
                body = q"""Left("err")"""
              ) ::
                protoMessage
                  .oneOfs
                  .map { protoField =>
                    val childTerm = Term.Name(protoField.scalaType)
                    Case(
                      pat = Pat.Extract(q"$grpcTerm.OneOf.$childTerm", List(Pat.Var(Term.Name("value")))),
                      cond = None,
                      body = q"value.fromGrpc"
                    )
                  }
            )

          val fromGrpcTree = q"""
                implicit class $fromGrpcImplicitClassName(lhs: $grpcType) {
                  def fromGrpc: Either[..$typesnel] = $matchStatement
                }
             """

          fmt(s"$toGrpcTree \n $fromGrpcTree")
      }

    def renderImplicits(fmt: String => String): String =
      scalaObjectToGrpc(lhs, fmt)

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
