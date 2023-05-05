package com.oracle.infy.wookiee.grpc.srcgen

import scala.meta.{Init, Name, Term, Type}

object SourceGenModel {
  // Custom extractor for Init
  object CustomInit {
    def unapply(init: Init): Option[(Type, Name, Seq[Seq[Term]])] =
      Some((init.tpe, init.name, init.argClauses.map(_.values)))
  }

  object CustomTypeApply {
    def unapply(t: Type.Apply): Option[(Type.Name, List[Type])] = {
      t.tpe match {
        case typeName: Type.Name => Some((typeName, t.argClause.values))
        case _ => None
      }
    }
  }

  object NestedTypeApply {
    def unapply(t: Type): Option[Type.Apply] = t match {
      case app: Type.Apply => Some(app)
      case _ => None
    }
  }

  final case class srcGenIgnoreClass() extends scala.annotation.StaticAnnotation
  final case class srcGenIgnoreField(field: String) extends scala.annotation.StaticAnnotation

  final case class GrpcConversionError(msg: String)

  final case class ParamModel(param: Term.Param, grpcType: String)

  final case class Model(
      scalaTypeName: String,
      grpcTypeName: String,
      oneOfs: List[ParamModel],
      fields: List[ParamModel]
  )

  sealed trait ScalaSource {
    def filter: String => Boolean
  }

  final case class ScalaTextSource(content: String, filter: String => Boolean) extends ScalaSource
  final case class ScalaFileSource(scalaFilePath: String, filter: String => Boolean) extends ScalaSource

  object ScalaTextSource {
    def apply(scalaFilePath: String): ScalaSource = ScalaTextSource(scalaFilePath, _ => true)
  }

  object ScalaFileSource {
    def apply(scalaFilePath: String): ScalaSource = ScalaFileSource(scalaFilePath, _ => true)
  }
}
