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

  private def betweenOuterBrackets(str: String) = {
    str.dropWhile(_ /== '[').drop(1).dropRight(1)
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
              val innerType = betweenOuterBrackets(other)
              val innerPt = toProtoType(innerType, sealedTypeLookup)
              innerPt match {
                case OptionType(t, scalaType) =>
                  OptionOptionType(t, scalaType)
                case t =>
                  OptionType(t, other)
              }

            case "List" =>
              val innerType = betweenOuterBrackets(other)
              ListType(toProtoType(innerType, sealedTypeLookup), other)
            case "Map" =>
              val innerTypes = betweenOuterBrackets(other)
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
  private def fromGrpc = "fromGrpc"

  protected def toProto(record: Record): String = {

    def toProtoCaseClass(record: CaseClass): String = {

      def protoTypeToStr(protoType: ProtoType, maybePrefix: Option[String]): String = {
        val prefix = maybePrefix.getOrElse("")
        protoType match {
          case PrimitiveType(t, _)      => t
          case DateTimeType(_)          => "int64"
          case OptionOptionType(_, s)   => s"${prefix}OptionOption${generateScalaType(s)}"
          case OptionType(_, scalaType) => s"${prefix}Option${generateScalaType(scalaType)}"
          case ListType(t, _)           => s"repeated ${protoTypeToStr(t, maybePrefix)}"
          case MapType(lt, rt, _)       => s"map<${protoTypeToStr(lt, maybePrefix)}, ${protoTypeToStr(rt, maybePrefix)}>"
          case CustomType(t, _, _)      => s"$prefix$t"
          case ErrorType(_, _)          => s"[[error]]"
        }
      }

      def fields(tuples: List[(String, String)]): String = {
        tuples
          .zipWithIndex
          .map {
            case ((name, t), i) =>
              s"  ${protoTypeToStr(toProtoType(t, Set.empty), Some(prefix))} $name = ${i + 1};"
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

  protected def grpcDecoder(record: Record, sealedTypeLookup: Set[String]): String = {

    def implicitClass(name: String, body: String, recordType: String) = {
      s"""  implicit class ${name}${fromGrpc.capitalize}(lhs: $prefix$name) {
         |    def $fromGrpc: Either[GrpcConversionError, $recordType] = {
         |$body
         |    }
         |  }""".stripMargin
    }

    record match {
      case SealedTrait(name, recordType, records) =>
        val body = zipWithLetter(records)
          .map {
            case (_, v) =>
              s"        .orElse(lhs.asMessage.sealedValue.$v.map(_.$fromGrpc))"
          }
          .mkString("      None\n", "\n", "\n        .getOrElse(Left(GrpcConversionError(\"Invalid sealed values\")))")

        val rootClass = implicitClass(name, body, recordType)
        records
          .map(r => grpcDecoder(r, sealedTypeLookup))
          .mkString(s"$rootClass\n", "\n", "")

      case CaseClass(name, recordType, members) =>
        val constructorName = if (recordType.startsWith("Option[Option[")) {
          "Option(Option"
        } else if (recordType.startsWith("Option[")) {
          "Option"
        } else {
          name
        }
        val applyPart = {
          if (recordType.startsWith("Option[Option[")) {
            s"$constructorName(value))"
          } else if (recordType.startsWith("Option[")) {
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
          if (recordType.startsWith("Option[Option[") & name.startsWith("NoneNone")) {
            s"      val _ = lhs\n      Right(None)"
          } else if (recordType.startsWith("Option[Option[")) {
            s"      val _ = lhs\n      Right(Some(None))"
          } else if (recordType.startsWith("Option[")) {
            s"      val _ = lhs\n      Right(None)"
          } else {
            s"      Right($applyPart)"
          }
        } else {
          members
            .map {
              case (fieldName, t) =>
                toProtoType(t, sealedTypeLookup) match {
                  case _: DateTimeType =>
                    s"        $fieldName <- toZonedDateTime(lhs.$fieldName)"
                  case CustomType(_, true, _) =>
                    s"        $fieldName <- lhs.$fieldName.$fromGrpc"
                  case CustomType(_, false, _) =>
                    s"        $fieldName <- lhs.get${fieldName.take(1).toUpperCase}${fieldName.drop(1)}.$fromGrpc"
                  case ListType(PrimitiveType(_, _), _) =>
                    s"        $fieldName <- Right(lhs.$fieldName.toList)"
                  case ListType(_, _) =>
                    s"        $fieldName <- lhs.$fieldName.toList.map(_.$fromGrpc).sequence"
                  case _: OptionType | _: OptionOptionType =>
                    s"        $fieldName <- lhs.$fieldName.$fromGrpc"
                  case _ =>
                    s"        $fieldName <- Right(lhs.$fieldName)"
                }
            }
            .mkString(s"      for {\n", "\n", s"\n      } yield $applyPart")
        }

        implicitClass(name, forPart, recordType)
    }
  }

  protected def grpcEncoder(record: Record, sealedTypeLookup: Set[String]): String = {

    def implicitClass(name: String, recordType: String, body: String) = {
      s"""  implicit class ${name}To$prefix(lhs: $recordType) {
         |    def to$prefix: $prefix$name = {
         |$body
         |    }
         |  }""".stripMargin
    }

    def optionOptionBody(recordType: String, wrapInSome: Boolean) = {
      val args = if (wrapInSome) {
        s"Some(v.to$prefix)"
      } else "v"
      s"""|       lhs match {
          |         case None =>  ${prefix}NoneNone${innerType(recordType)}()
          |         case Some(Some(v)) => ${prefix}SomeSome${innerType(recordType)}($args)
          |         case Some(None) => ${prefix}SomeNone${innerType(recordType)}()
          |       }""".stripMargin
    }

    record match {
      case SealedTrait(name, recordType, records) =>
        if (recordType.startsWith("Option")) {

          val protoT = toProtoType(recordType, sealedTypeLookup)
          val body = protoT match {
            case OptionOptionType(_: CustomType, _) =>
              optionOptionBody(recordType, wrapInSome = true)
            case OptionOptionType(_, _) =>
              optionOptionBody(recordType, wrapInSome = false)
            case OptionType(_: CustomType, _) =>
              s"""|      lhs match {
                  |        case None =>  ${prefix}None${generateScalaType(recordType)}()
                  |        case Some(v) => ${prefix}Some${generateScalaType(recordType)}(Some(v.to${prefix}))
                  |      }""".stripMargin
            case _ =>
              s"""|      lhs match {
                  |        case None =>  ${prefix}None${generateScalaType(recordType)}()
                  |        case Some(v) => ${prefix}Some${generateScalaType(recordType)}(v)
                  |      }""".stripMargin
          }
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
                case _: OptionType | _: OptionOptionType =>
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
              val typeWithoutPackage = s"Option[${stripPackageNames(betweenOuterBrackets(t))}]"
              SealedTrait(
                s"Option$name",
                typeWithoutPackage,
                List(
                  CaseClass(s"None$name", typeWithoutPackage, List.empty),
                  CaseClass(s"Some$name", typeWithoutPackage, List("value" -> betweenOuterBrackets(t)))
                )
              )
          }
          .toSet

    }
  }

  private def scanForOptionOptionFields(record: Record, sealedTypeLookup: Set[String]): Set[Record] = {
    record match {
      case SealedTrait(_, _, records) =>
        records
          .flatMap(a => scanForOptionOptionFields(a, sealedTypeLookup))
          .toSet
      case CaseClass(_, _, members) =>
        members
          .map {
            case (_, t) =>
              t -> toProtoType(t, sealedTypeLookup)
          }
          .collect {
            case (t, ot: OptionOptionType) => t -> ot
          }
          .map {
            case (t, _) =>
              val name = stripPackageNames(innerType(t))
              val typeWithoutPackage = s"Option[Option[$name]]"
              SealedTrait(
                s"OptionOption$name",
                typeWithoutPackage,
                List(
                  CaseClass(s"NoneNone$name", typeWithoutPackage, List.empty),
                  CaseClass(s"SomeSome$name", typeWithoutPackage, List("value" -> name)),
                  CaseClass(s"SomeNone$name", typeWithoutPackage, List.empty)
                )
              )
          }
          .toSet

    }
  }

  def genScala(records: List[Record], sealedTypeLookup: Set[String], header: String): String = {
    val recs = addOptionRecords(records, sealedTypeLookup)

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

  protected def addOptionRecords(records: List[Record], sealedTypeLookup: Set[String]): List[Record] = {
    val optionRecords =
      records
        .flatMap(a => scanForOptionFields(a, sealedTypeLookup))
        .toSet

    val optionOptionRecords =
      records
        .flatMap(a => scanForOptionOptionFields(a, sealedTypeLookup))
        .toSet

    records ++ optionRecords ++ optionOptionRecords
  }

  def genProto(records: List[Record], sealedTypeLookup: Set[String]): String = {
    val recs = addOptionRecords(records, sealedTypeLookup)

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
    genServices(
      List((serviceName, rpcs)),
      records,
      sealedTypeLookup,
      classPackage
    )
  }

  def genServices(
      services: List[(String, List[(Type, String)])],
      records: List[Record],
      sealedTypeLookup: Set[String],
      classPackage: String
  ): String = {

    def rpcStr(rpcs: List[(Type, String)]) =
      rpcs
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

    def serviceStr(serviceName: String, rpcs: List[(Type, String)]): String = {
      s"""
        |service $serviceName {
        |${rpcStr(rpcs)}
        |}
        |""".stripMargin
    }

    s"""
       |// NOTE: This code is generated. DO NOT EDIT!!
       |syntax = "proto3";
       |
       |package $classPackage;
       |
       |${services.map { case (serviceName, rpcs) => serviceStr(serviceName, rpcs) }.mkString("\n")}
       |
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
  // Option[Option[String]] -> OptionString
  private def generateScalaType(str: String): String = {
    str.split(s"\\[").drop(1).map(_.filterNot(_ === ']')).map(stripPackageNames).mkString
  }

  // ...[aa] -> aa
  // ...[aa[bb]] --> bb
  // ...[aa[bb[cc]]] -> cc
  private def innerType(str: String): String = {
    str.split(s"\\[").lastOption.map(_.takeWhile(_ /== ']')).getOrElse("")
  }

}
