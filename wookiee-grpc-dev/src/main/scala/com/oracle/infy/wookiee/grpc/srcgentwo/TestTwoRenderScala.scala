package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.TestTwo.Model

import scala.meta._

object TestTwoRenderScala {

  def renderScala(model: Model, fmt: String => String): String =
    model match {
      // Case Class
      case Model(name, _, Nil, fields) =>
        val modelTerm = Term.Name(name)
        val modelType = Type.Name(name)

        val fromGrpcImplicitClassName = Type.Name(s"${name}FromGrpc")
        val toGrpcImplicitClassName = Type.Name(s"${name}ToGrpc")

        val grpcType = Type.Name(s"Grpc$name")
        val returnTypeTerm = Term.Name(s"Grpc$name")

        val newFields = fields
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
                    $returnTypeTerm(..$newFields)
                  }
                }
            """

        val typesnel = List(Type.Name("String"), modelType)

        val enumeratorsnel = fields
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

        val forAssign = fields
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

      // Trait
      case Model(name, _, oneOfs, Nil) =>
        val modelType = Type.Name(name)

        val fromGrpcImplicitClassName = Type.Name(s"${name}FromGrpc")
        val toGrpcImplicitClassName = Type.Name(s"${name}ToGrpc")

        val grpcType = Type.Name(s"Grpc$name")
        val grpcTerm = Term.Name(s"Grpc$name")

        val toGrpcMatchStatement = Term.Match(
          Term.Name("lhs"),
          oneOfs
            .map { param =>
              val paramTypeStr = param.decltpe match {
                case Some(Type.Name(value)) => value
                case None                   => "Unknown"
              }
              val childTerm = Term.Name(paramTypeStr)
              val childType = Type.Name(paramTypeStr)

              Case(
                pat = Pat.Typed(Pat.Var(Term.Name("value")), childType),
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
               def toGrpc: $grpcType = $toGrpcMatchStatement
             }
           """

        val typesnel = List(Type.Name("String"), modelType)

        val fromGrpcMatchStatement =
          Term.Match(
            Term.Select(Term.Name("lhs"), Term.Name("oneOf")),
            Case(
              pat = Term.Select(Term.Select(grpcTerm, Term.Name("OneOf")), Term.Name("Empty")),
              cond = None,
              body = q"""Left("err")"""
            ) :: oneOfs
              .map { param =>
                val paramTypeStr = param.decltpe match {
                  case Some(Type.Name(value)) => value
                  case None                   => "Unknown"
                }
                val childTerm = Term.Name(paramTypeStr)
                Case(
                  pat = Pat.Extract(q"$grpcTerm.OneOf.$childTerm", List(Pat.Var(Term.Name("value")))),
                  cond = None,
                  body = q"value.fromGrpc"
                )
              }
          )

        val fromGrpcTree = q"""
                implicit class $fromGrpcImplicitClassName(lhs: $grpcType) {
                  def fromGrpc: Either[..$typesnel] = $fromGrpcMatchStatement
                }
             """

        fmt(s"$toGrpcTree \n $fromGrpcTree")

      // Invalid model
      case Model(name, _, oneOfs, fields) =>
        s"Error: $name $oneOfs $fields"
    }
}
