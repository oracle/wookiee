package com.oracle.infy.wookiee.grpc.srcgentwo

import scala.meta.Term

object SourceGenModel {

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
