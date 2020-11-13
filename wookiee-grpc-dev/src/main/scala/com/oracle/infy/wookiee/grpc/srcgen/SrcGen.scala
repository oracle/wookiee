package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.Model._
import com.oracle.infy.wookiee.grpc.srcgen.implicits._

import scala.reflect.runtime.universe._

trait SrcGen {

  def sealedTypes(types: List[Symbol]): Set[String] = {
    types
      .foldLeft(Set.empty[String])((acc, a) => {
        if (a.asClass.knownDirectSubclasses.isEmpty) {
          acc
        } else {
          acc + a.name.toString
        }
      })
  }

  private def between(start: Char, str: String) = {
    str.dropWhile(_ /== start).drop(1).dropRight(1)
  }

  private def toProtoType(t: String, sealedTypeLookup: Set[String]): ProtoType = {
    t match {
      case "String"        => PrimitiveType("string", "String")
      case "Int"           => PrimitiveType("int32", "Int")
      case "Long"          => PrimitiveType("int64", "Long")
      case "Boolean"       => PrimitiveType("bool", "Boolean")
      case "ZonedDateTime" => DateTimeType("ZonedDateTime")
      case other =>
        if (other.contains("[")) {
          other.takeWhile(_ /== '[') match {
            case "Option" =>
              val innerType = between('[', other)
              OptionType(toProtoType(innerType, sealedTypeLookup), other)

            case "List" =>
              val innerType = between('[', other)
              ListType(toProtoType(innerType, sealedTypeLookup), other)
            case "Map" =>
              val innerTypes = between('[', other)
              val innerType1 = innerTypes.split(",").headOption.getOrElse("unknown")
              val innerType2 = innerTypes.split(",").lastOption.getOrElse("unknown")
              MapType(
                toProtoType(innerType1, sealedTypeLookup),
                toProtoType(innerType2, sealedTypeLookup),
                other
              )
            case unknown => CustomType(unknown, sealedTypeLookup.contains(unknown), unknown)
          }
        } else {
          if (other.contains(".")) {
            toProtoType(stripPackageNames(other), sealedTypeLookup)
          } else {
            CustomType(other, sealedTypeLookup.contains(other), other)
          }
        }
    }
  }

  def stripPackageNames(innerType: String): String = {
    if (innerType.contains(".")) {
      innerType.split("\\.").lastOption.getOrElse("unknown")
    } else {
      innerType
    }
  }

  def toRecord(s: Symbol): Record = {
    val subClasses = s.asClass.knownDirectSubclasses
    if (subClasses.isEmpty) {
      val name = s.name.toString
      val members = s
        .typeSignature
        .members
        .filter(!_.isMethod)
        .map { m =>
          m.name.toString.trim -> m.typeSignature.toString
        }
        .toList
      CaseClass(name, name, members)
    } else {
      SealedTrait(s.name.toString, s.name.toString, subClasses.map(s => toRecord(s)).toList)
    }
  }

  private def prefix = "Grpc"

  private def toProto(record: Record): String = {

    def toProtoCaseClass(record: CaseClass): String = {

      def protoTypeToStr(protoType: ProtoType): String = {
        protoType match {
          case PrimitiveType(t, _)      => t
          case DateTimeType(_)          => "int64"
          case OptionType(_, scalaType) => s"${prefix}Option${generateScalaType(scalaType)}"
          case ListType(t, _)           => s"repeated ${protoTypeToStr(t)}"
          case MapType(lt, rt, _)       => s"map<${protoTypeToStr(lt)}, ${protoTypeToStr(rt)}>"
          case CustomType(t, _, _)      => s"$prefix$t"
        }
      }

      def fields(tuples: List[(String, String)]): String = {
        tuples
          .zipWithIndex
          .map {
            case ((name, t), i) =>
              s"  ${protoTypeToStr(toProtoType(t, Set.empty))} $name = ${i + 1};"
          }
          .mkString("\n")
      }
      s"""
         |// DO NOT EDIT! (this code is generated)
         |message $prefix${record.name} {
         |${fields(record.members)}
         |}
         |""".stripMargin
    }

    record match {
      case SealedTrait(name, _, records) =>
        val sealedValues = zipWithLetter(records)
          .zipWithIndex
          .map {
            case ((t, name), i) =>
              s"$prefix$t $name = ${i + 1};"
          }
          .map(a => s"    $a")
          .mkString("\n")

        val protos = records
          .map(toProto)
          .mkString("")

        s"""
           |// DO NOT EDIT! (this code is generated)
           |message $prefix$name {
           |  oneof sealed_value {
           |$sealedValues
           |  }
           |}
           |$protos""".stripMargin
      case caseClass: CaseClass =>
        toProtoCaseClass(caseClass)
    }
  }

  private def grpcDecoder(record: Record, sealedTypeLookup: Set[String]): String = {

    def implicitClass(name: String, body: String, recordType: String) = {
      s"""  implicit class ${name}ToADR(lhs: $prefix$name) {
         |    def toADR: Either[GrpcConversionError, $recordType] = {
         |$body
         |    }
         |  }""".stripMargin
    }

    record match {
      case SealedTrait(name, recordType, records) =>
        val body = zipWithLetter(records)
          .map {
            case (_, v) =>
              s"        .orElse(lhs.asMessage.sealedValue.$v.map(_.toADR))"
          }
          .mkString("None", "\n", """.getOrElse(Left(GrpcConversionError("Invalid sealed values")))""")

        val rootClass = implicitClass(name, body, recordType)
        records
          .map(r => grpcDecoder(r, sealedTypeLookup))
          .mkString(s"$rootClass\n", "\n", "")

      case CaseClass(name, recordType, members) =>
        val constructorName = if (recordType.startsWith("Option")) {
          "Option"
        } else {
          name
        }
        val applyPart = {
          if (recordType.startsWith("Option") && members.size === 1) {
            s"$constructorName(value)"
          } else {
            members
              .map {
                case (m, _) =>
                  s"$m = $m"
              }
              .mkString(s"$constructorName(", ",", ")")
          }
        }

        val forPart = if (members.isEmpty) {
          // Turn custom None types into scala None
          if (recordType.startsWith("Option")) {
            s"val _ = lhs\nRight(None)"
          } else {
            s"Right($applyPart)"
          }
        } else {
          members
            .map {
              case (fieldName, t) =>
                toProtoType(t, sealedTypeLookup) match {
                  case _: DateTimeType =>
                    s"        $fieldName <- toZonedDateTime(lhs.$fieldName)"
                  case CustomType(_, true, _) =>
                    s"        $fieldName <- lhs.$fieldName.toADR"
                  case CustomType(_, false, _) =>
                    s"        $fieldName <- lhs.get${fieldName.take(1).toUpperCase}${fieldName.drop(1)}.toADR"
                  case ListType(PrimitiveType(_, _), _) =>
                    s"        $fieldName <- Right(lhs.$fieldName.toList)"
                  case ListType(_, _) =>
                    s"        $fieldName <- lhs.$fieldName.toList.map(_.toADR).sequence"
                  case OptionType(_, _) =>
                    s"        $fieldName <- lhs.$fieldName.toADR"
                  case _ =>
                    s"        $fieldName <- Right(lhs.$fieldName)"
                }
            }
            .mkString(s"      for {\n", "\n", s"\n} yield $applyPart")
        }

        implicitClass(name, forPart, recordType)
    }
  }

  private def grpcEncoder(record: Record, sealedTypeLookup: Set[String]): String = {
    def implicitClass(name: String, recordType: String, body: String) = {
      s"""  implicit class ${name}To$prefix(lhs: $recordType) {
         |    def to$prefix: $prefix$name = {
         |$body
         |    }
         |  }""".stripMargin
    }

    record match {
      case SealedTrait(name, recordType, records) =>
        if (recordType.startsWith("Option")) {
          val body = s"""
                        |lhs match {
                        |  case None =>  ${prefix}None${generateScalaType(recordType)}()
                        |  case Some(v) => ${prefix}Some${generateScalaType(recordType)}(v)
                        |}
                        |""".stripMargin.trim
          implicitClass(name, recordType, body)
        } else {
          val body =
            zipWithLetter(records)
              .map {
                case (name, v) =>
                  s"        case $v: $name => $v.to$prefix"
              }
              .mkString("      lhs match {\n", "\n", "\n      }")

          val rootClass = implicitClass(name, recordType, body)
          records
            .map(record => grpcEncoder(record, sealedTypeLookup))
            .mkString(s"$rootClass\n", "\n", "")
        }

      case CaseClass(name, recordType, members) =>
        val apply = members
          .map {
            case (name, t) =>
              toProtoType(t, sealedTypeLookup) match {
                case customType: CustomType =>
                  if (customType.isMemberOfSealedTrait) {
                    s"        $name = lhs.$name.to$prefix"
                  } else {
                    s"        $name = Some(lhs.$name.to$prefix)"
                  }
                case _: DateTimeType =>
                  s"        $name = zonedDateTimeToLong(lhs.$name)"
                case ListType(PrimitiveType(_, _), _) =>
                  s"        $name = lhs.$name"
                case ListType(CustomType(_, _, _), _) =>
                  s"        $name = lhs.$name.map(_.to$prefix)"
                case OptionType(_, _) =>
                  s"        $name = lhs.$name.to$prefix"
                case _ =>
                  s"        $name = lhs.$name"
              }
          }
          .mkString(s"      $prefix$name(\n", ",\n", "\n      )")

        implicitClass(name, recordType, apply)
    }
  }

  private def scanForOptionFields(record: Record, sealedTypeLookup: Set[String]): Set[Record] = {
    record match {
      case SealedTrait(_, _, records) =>
        records
          .flatMap(a => scanForOptionFields(a, sealedTypeLookup))
          .toSet
      case CaseClass(_, _, members) =>
        members
          .map {
            case (_, t) =>
              t -> toProtoType(t, sealedTypeLookup)
          }
          .collect {
            case (t, ot: OptionType) => t -> ot
          }
          .map {
            case (t, _) =>
              val name = stripPackageNames(generateScalaType(t))
              val typeWithoutPackage = s"Option[${stripPackageNames(between('[', t))}]"
              SealedTrait(
                s"Option$name",
                typeWithoutPackage,
                List(
                  CaseClass(s"None$name", typeWithoutPackage, List.empty),
                  CaseClass(s"Some$name", typeWithoutPackage, List("value" -> between('[', t)))
                )
              )
          }
          .toSet

    }
  }

  def genScala(records: List[Record], sealedTypeLookup: Set[String], header: String): String = {
    val optionRecords =
      records
        .flatMap(a => scanForOptionFields(a, sealedTypeLookup))
        .toSet

    val recs =
      records ++ optionRecords

    val body = recs
      .map(r => grpcEncoder(r, sealedTypeLookup) ++ "\n\n" ++ grpcDecoder(r, sealedTypeLookup))
      .mkString("\n")

    s"""
       |$header
       |
       |import cats.implicits._
       |import scala.util.Try
       |
       |// NOTE: This code is generated. DO NOT EDIT!
       |object implicits {
       |
       |  private def toZonedDateTime(l: Long): Either[GrpcConversionError, ZonedDateTime] = {
       |    Try {
       |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(l), ZoneId.of("UTC"))
       |    }.toEither
       |      .left.map(t => GrpcConversionError(t.getMessage))
       |  }
       |
       |  private def zonedDateTimeToLong(zdt: ZonedDateTime): Long = {
       |    zdt.toEpochSecond
       |  }
       |$body
       |}""".stripMargin
  }

  def genProto(records: List[Record], sealedTypeLookup: Set[String]): String = {

    val optionRecords = records
      .flatMap(a => scanForOptionFields(a, sealedTypeLookup))
      .toSet

    val recs = records ++ optionRecords

    recs
      .map(toProto)
      .mkString("")

  }

  def genService(
      rpcs: List[(Type, String)],
      records: List[Record],
      sealedTypeLookup: Set[String],
      classPackage: String,
      serviceName: String
  ): String = {

    val rpcStr = rpcs
      .map {
        case (t, name) =>
          t.typeArgs.map(_.typeSymbol.name.toString) match {
            case input :: output :: Nil => RPC(name, input, output)
            case _                      => RPC(name, "CodeGenErr", "CodeGenErr")
          }
      }
      .map {
        case RPC(name, input, output) =>
          s"""
             |  // DO NOT EDIT! (this code is generated)
             |  rpc $name($prefix$input) returns ($prefix$output) {}""".stripMargin
      }
      .mkString("\n")

    s"""
       |// NOTE: This code is generated. DO NOT EDIT!!
       |syntax = "proto3";
       |
       |package $classPackage;
       |
       |service $serviceName {
       |$rpcStr
       |}
       |${genProto(records, sealedTypeLookup)}
       |""".stripMargin
  }

  private def zipWithLetter(records: List[Record]): List[(String, String)] = {
    records
      .map(_.name)
      .zipWithIndex
      .map(a => a._1 -> (97 + a._2).toChar.toString)
  }

  //  private def upCaseFirst(in: String): String = in.take(1).toUpperCase ++ in.drop(1)

  // ...[aa] -> aa
  // ...[aa[bb]] --> aabb
  // ...[aa[bb[cc]]] -> aabbcc
  private def generateScalaType(str: String): String = {
    str.split(s"\\[").drop(1).map(_.filterNot(_ === ']')).map(stripPackageNames).mkString
  }

}
