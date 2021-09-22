package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.GrpcSourceGenTwo.{
  Model,
  getGrpcType,
  isListNonScalarType,
  isListScalarType,
  isScalarType,
  isValidNonScalarMapType,
  isValidScalarMapType
}

import scala.meta._

object GrpcSourceGenRenderScalaTwo {

  private def getClassName(tpe: Type, outerType: String): String =
    tpe match {
      case Type.Apply(Type.Name(`outerType`), Type.Name(innerType) :: Nil) =>
        s"$outerType$innerType"
      case Type.Apply(Type.Name(`outerType`), innerType :: Nil) =>
        s"$outerType${getClassName(innerType, outerType)}"
      case _ => ""
    }

  private val grpcConversionErrorTypeName = Type.Name("GrpcConversionError")

  def renderScalaOptional(t: Type, fmt: String => String): String = {

    val toGrpcImplicitClassName = Type.Name(s"${getClassName(t, "Option")}ToGrpc")
    val fromGrpcImplicitClassName = Type.Name(s"${getClassName(t, "Option")}FromGrpc")
    val grpcType = Type.Name(getGrpcType(Some(t)))
    val grpcTerm = Term.Name(getGrpcType(Some(t)))

    val toApply = t match {
      case Type.Apply(Type.Name("Option"), innerType :: Nil) if isScalarType(innerType) =>
        q"value"
      case Type.Apply(Type.Name("Option"), Type.Apply(Type.Name("List"), Type.Name("String") :: Nil) :: Nil) =>
        q"GrpcListString(value)"
      case _ =>
        q"value.toGrpc"
    }

    val fromApply = t match {
      case Type.Apply(Type.Name("Option"), innerType :: Nil) if isScalarType(innerType) =>
        q"Right(Some(value))"
      case Type.Apply(Type.Name("Option"), Type.Apply(Type.Name("List"), Type.Name("String") :: Nil) :: Nil) =>
        q"Right(Some(value.list.toList))"
      case _ =>
        q"value.fromGrpc.map(Some(_))"
    }

    val toGrpcTree =
      q"""
            implicit class $toGrpcImplicitClassName(lhs: $t) {
              def toGrpc: $grpcType = {
                lhs match {
                  case None => $grpcTerm($grpcTerm.OneOf.Nonne(GrpcNonne()))
                  case Some(value) => $grpcTerm($grpcTerm.OneOf.Somme($toApply))
                }
              }
            }
         """

    val matchStatement = Term.Match(
      q"lhs.oneOf",
      List(
        Case(Pat.Extract(q"$grpcTerm.OneOf.Somme", List(Pat.Var(q"value"))), None, fromApply),
        Case(Pat.Wildcard(), None, q"Right(None)")
      )
    )

    val fromGrpcTree =
      q"""
          implicit class $fromGrpcImplicitClassName(lhs: $grpcType) {
            def fromGrpc: Either[$grpcConversionErrorTypeName, $t] = $matchStatement
          }
        """

    fmt(toGrpcTree.toString() + "\n" + fromGrpcTree.toString())

  }

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
          .map { paramModel =>
            val paramName = paramModel.param.name.value
            val paramNameTerm = Term.Name(paramName)

            val nonScalarAssign = Term.Assign(
              paramNameTerm,
              q"Some(lhs.$paramNameTerm.toGrpc)"
            )

            val scalarAssign = Term.Assign(paramNameTerm, Term.Select(Term.Name("lhs"), paramNameTerm))

            paramModel
              .param
              .decltpe
              .map { t =>
                if (isScalarType(t)) {
                  scalarAssign
                } else if (isListScalarType(t)) {
                  scalarAssign
                } else if (isListNonScalarType(t)) {
                  Term.Assign(paramNameTerm, q"lhs.$paramNameTerm.map(_.toGrpc)")
                } else if (isValidScalarMapType(t)) {
                  scalarAssign
                } else if (isValidNonScalarMapType(t)) {
                  Term.Assign(paramNameTerm, q"lhs.$paramNameTerm.map(entry => (entry._1, entry._2.toGrpc))")
                } else {
                  nonScalarAssign
                }
              }
              .getOrElse(nonScalarAssign)
          }

        val toBody = if (newFields.isEmpty) {
          q"""
              val _ = lhs
              $returnTypeTerm(..$newFields)
             """
        } else {
          q"""$returnTypeTerm(..$newFields)"""
        }

        val toGrpc =
          q"""
                implicit class $toGrpcImplicitClassName (lhs: $modelType) {
                  def toGrpc: $grpcType = {
                    $toBody
                  }
                }
            """

        val typesnel = List(grpcConversionErrorTypeName, modelType)

        val enumeratorsnel = fields
          .map { paramModel =>
            val paramName = paramModel.param.name.value
            val upperCaseParamName = paramName.take(1).toUpperCase + paramName.drop(1)

            val paramNameTerm = Term.Name(paramName)
            val t = Term.Select(Term.Name("lhs"), paramNameTerm)
            val app = q"Right($t)"
            val scalarGenerator = Enumerator.Generator(Pat.Var(paramNameTerm), app)

            val nonScalarGenerator =
              Enumerator.Generator(
                Pat.Var(paramNameTerm),
                q"lhs.${Term.Name(s"get$upperCaseParamName")}.fromGrpc"
              )

            paramModel
              .param
              .decltpe
              .map { t =>
                if (isScalarType(t)) {
                  scalarGenerator
                } else if (isListScalarType(t)) {
                  Enumerator
                    .Generator(
                      Pat.Var(paramNameTerm),
                      q"Right(lhs.$paramNameTerm.toList)"
                    )
                } else if (isListNonScalarType(t)) {
                  Enumerator
                    .Generator(
                      Pat.Var(paramNameTerm),
                      q"lhs.$paramNameTerm.map(_.fromGrpc).foldLeft(Right(Nil): Either[$grpcConversionErrorTypeName, $t]){ case (acc, i) => i.flatMap(a => acc.map(b => a :: b))}"
                    )
                } else if (isValidScalarMapType(t)) {
                  Enumerator
                    .Generator(
                      Pat.Var(paramNameTerm),
                      q"Right(lhs.$paramNameTerm)"
                    )
                } else if (isValidNonScalarMapType(t)) {
                  Enumerator
                    .Generator(
                      Pat.Var(paramNameTerm),
                      q"Right(lhs.$paramNameTerm.map(entry => (entry._1, entry._2.fromGrpc)).collect { case (a, Right(b)) => (a, b) }.toMap)"
                    )
                } else {
                  nonScalarGenerator
                }
              }
              .getOrElse(nonScalarGenerator)
          }

        val forAssign = fields
          .map { paramModel =>
            val p = Term.Name(paramModel.param.name.value)
            Term.Assign(p, p)
          }

        val forYield = if (enumeratorsnel.nonEmpty) {
          q"for(..$enumeratorsnel) yield $modelTerm(..$forAssign)"
        } else {
          q"""
             val _ = lhs
             Right($modelTerm())
             """
        }

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
            .map { paramModel =>
              val paramTypeStr = paramModel.param.decltpe match {
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

        val typesnel = List(grpcConversionErrorTypeName, modelType)

        val errorString = s"Unable to convert object from grpc type: ${model.grpcTypeName}"

        val fromGrpcMatchStatement =
          Term.Match(
            Term.Select(Term.Name("lhs"), Term.Name("oneOf")),
            Case(
              pat = Term.Select(Term.Select(grpcTerm, Term.Name("OneOf")), Term.Name("Empty")),
              cond = None,
              //todo -- can't seem to use grpcConversionErrorTypeName value here
              body = q"""Left(GrpcConversionError($errorString))"""
            ) :: oneOfs
              .map { paramModel =>
                val paramTypeStr = paramModel.param.decltpe match {
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
