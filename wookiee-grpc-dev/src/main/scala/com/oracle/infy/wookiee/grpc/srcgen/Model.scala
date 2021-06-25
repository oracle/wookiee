package com.oracle.infy.wookiee.grpc.srcgen

object Model {

  sealed trait Record {
    def name: String
    def recordType: String
  }

  final case class SealedTrait(name: String, recordType: String, records: List[Record]) extends Record
  final case class CaseClass(name: String, recordType: String, members: List[(String, String)]) extends Record

  sealed trait ProtoType {
    def scalaType: String
  }
  final case class PrimitiveType(t: String, scalaType: String) extends ProtoType
  final case class ListType(t: ProtoType, scalaType: String) extends ProtoType
  final case class OptionType(t: ProtoType, scalaType: String) extends ProtoType
  final case class OptionOptionType(t: ProtoType, scalaType: String) extends ProtoType
  final case class MapType(lt: ProtoType, rt: ProtoType, scalaType: String) extends ProtoType
  final case class CustomType(t: String, isMemberOfSealedTrait: Boolean, scalaType: String) extends ProtoType
  final case class DateTimeType(scalaType: String) extends ProtoType

  final case class IO[I, O]()
  final case class RPC(name: String, input: String, output: String)
}
