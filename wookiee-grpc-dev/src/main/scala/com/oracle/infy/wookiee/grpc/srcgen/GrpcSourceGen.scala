// scalafix:off
package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.SourceGenModel._
import com.oracle.infy.wookiee.grpc.srcgen.implicits.MultiversalEquality

import scala.meta.{Term, _}

// TODO: This class will need a major refactor to migrate past deprecated unapply patterns
object GrpcSourceGen {

  implicit class Transpiler(lhs: Model) {

    private def renderOneOfs(oneOfs: List[ParamModel]): String =
      oneOfs
        .zipWithIndex
        .map {
          case (paramModel, index) =>
            val paramName = paramModel.param.name.value
            val paramGrpcType = paramModel.grpcType
            s"    $paramGrpcType $paramName = ${index + 1};"
        }
        .mkString("  oneof OneOf {\n", "\n", "\n  }")

    private def renderFields(fields: List[ParamModel], offset: Int): String =
      fields
        .zipWithIndex
        .map {
          case (paramModel, index) =>
            val paramName = paramModel.param.name.value
            val paramGrpcType = paramModel.grpcType

            s"  $paramGrpcType $paramName = ${index + offset};"
        }
        .mkString("\n")

    def renderProto: String =
      lhs match {
        case Model(_, grpcTypeName, Nil, Nil) =>
          s"""
             |message $grpcTypeName {
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, oneOfs, Nil) =>
          s"""
             |message $grpcTypeName {
             |${renderOneOfs(oneOfs)}
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, Nil, fields) =>
          s"""
             |message $grpcTypeName {
             |${renderFields(fields, offset = 1)}
             |}
             |""".stripMargin

        case Model(_, grpcTypeName, oneOfs, fields) =>
          s"""
             |message $grpcTypeName {
             |${renderOneOfs(oneOfs)}
             |${renderFields(fields, offset = oneOfs.length)}
             |}
             |""".stripMargin

      }
  }

  def expandNestedType(input: List[Model], outerType: String): Set[Type] = {
    def expand(t: Type.Apply): List[Type] =
      t match {
        case Type.Apply(Type.Name(`outerType`), Type.Name(_) :: Nil) => List(t)
        case Type.Apply(Type.Name(`outerType`), (app @ Type.Apply(_, _)) :: Nil) =>
          t :: expand(app)
        case _ => Nil
      }

    input
      .flatMap(_.fields)
      .flatMap(_.param.decltpe)
      .collect {
        case t @ Type.Apply(Type.Name(`outerType`), _) => t
      }
      .flatMap(expand)
      .groupBy(_.toString())
      .map(entry => (entry._1, entry._2.headOption))
      .values
      .flatten
      .toSet
  }

  def getOptionalTypes(input: List[Model]): Set[Type] =
    expandNestedType(input, "Option")

  final case class HandleTypeReturn(models: Set[Model], grpcType: String)

  private def handleTypeReturnOption(
      acc: Set[Model],
      noneType: Type.Name,
      innerModel: Set[Model],
      innerTypeName: String,
      newTypeName: String
  ): HandleTypeReturn =
    HandleTypeReturn(
      acc ++ Set(
        Model(
          // Using scala type for both because at the end scala type is converted to grpc type
          // If we call grpcType(newTypeName), we end up with types like "GrpcMaybeGrpcMaybe"
          newTypeName,
          newTypeName,
          oneOfs = List(
            ParamModel(
              Term.Param(
                mods = Nil,
                name = Term.Name("somme"),
                //// Type.Apply(List... Type.Name(String..)
                decltpe = Some(Type.Name(innerTypeName)),
                default = None
              ),
              //// repeat string foo...
              innerTypeName
            ),
            ParamModel(
              Term.Param(mods = Nil, name = Term.Name("nonne"), decltpe = Some(noneType), default = None),
              "Nonne"
            )
          ),
          fields = Nil
        )
      ) ++ innerModel,
      newTypeName
    )

  def synthesizeOptionModel(input: List[Model]): Set[Model] = {
    def handleType(t: Type, acc: Set[Model]): HandleTypeReturn = {
      val noneType = Type.Name("Nonne")

      t match {
        case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
          val newTypeName = "Maybe" + innerType
          handleTypeReturnOption(acc, noneType, Set.empty, innerType, newTypeName)

        //TODO -- handle lists and maps
        case Type.Apply(
            Type.Name("Option"),
            (listType @ Type.Apply(Type.Name("List"), Type.Name(innerTypeName) :: Nil)) :: Nil
            ) =>
          val newTypeName = "MaybeList" + innerTypeName
          val typeName = s"List$innerTypeName"

          HandleTypeReturn(
            acc ++ Set(
              Model(
                // Using scala type for both because at the end scala type is converted to grpc type
                // If we call grpcType(newTypeName), we end up with types like "GrpcMaybeGrpcMaybe"
                newTypeName,
                newTypeName,
                oneOfs = List(
                  ParamModel(
                    Term.Param(
                      mods = Nil,
                      name = Term.Name("somme"),
                      decltpe = Some(listType),
                      default = None
                    ),
                    typeName
                  ),
                  ParamModel(
                    Term.Param(mods = Nil, name = Term.Name("nonne"), decltpe = Some(noneType), default = None),
                    "Nonne"
                  )
                ),
                fields = Nil
              ),
              Model(
                typeName, // Check if scala gen works
                typeName,
                oneOfs = Nil,
                fields = List(
                  ParamModel(
                    Term.Param(mods = Nil, name = Term.Name("list"), decltpe = Some(listType), default = None),
                    getGrpcType(Some(listType))
                  )
                )
              )
            ),
            newTypeName
          )

        case Type.Apply(Type.Name("Option"), head :: Nil) =>
          val handletypeReturn = handleType(head, acc)
          val (innerModel, innerTypeName) = (handletypeReturn.models, handletypeReturn.grpcType)
          val newTypeName = "Maybe" + innerTypeName

          handleTypeReturnOption(acc, noneType, innerModel, innerTypeName, newTypeName)

        case _ => HandleTypeReturn(acc, "")
      }
    }

    val expandedOptions = input
      .foldLeft(Set.empty[Model]) {
        case (acc, model) =>
          model.fields match {
            case ::(_, _) =>
              model
                .fields
                .flatMap(_.param.decltpe)
                .flatMap(t => handleType(t, acc).models)
                .toSet
            case Nil =>
              acc
          }

      }

    if (expandedOptions.nonEmpty) {
      // Having it as a set didn't auto dedupe
      val dedupedExpandedOptions = expandedOptions
        .groupBy(_.grpcTypeName)
        .map(entry => (entry._1, entry._2.headOption))
        .values
        .flatten
        .toSet
        .map { model: Model =>
          model.copy(
            grpcTypeName = getGrpcType(Some(Type.Name(model.scalaTypeName))),
            oneOfs = model.oneOfs.map { paramModel =>
              paramModel.copy(grpcType = getGrpcType(Some(Type.Name(paramModel.grpcType))))
            }
          )
        }

      (dedupedExpandedOptions + Model(
        scalaTypeName = "None",
        grpcTypeName = getGrpcType(Some(Type.Name("Nonne"))),
        oneOfs = Nil,
        fields = Nil
      ))

    } else {
      expandedOptions
    }

  }

  def addParentsToSealedTraitMap(
      inits: List[Init],
      childTypeName: String,
      sealedTraitMap: Map[String, List[ParamModel]]
  ): Map[String, List[ParamModel]] =
    inits.foldLeft(sealedTraitMap) { (innerMap, init) =>
      init.tpe match {
        case Type.Name(parentClass) =>
          val currentMembers = innerMap.getOrElse(parentClass, List.empty)
          val childType = Type.Name(childTypeName)
          innerMap + (parentClass -> (currentMembers :+
            ParamModel(
              Term.Param(
                Nil,
                Term.Name(childTypeName.take(1).toLowerCase + childTypeName.drop(1)),
                Some(childType),
                None
              ),
              getGrpcType(Some(childType))
            )))
        case t =>
          println(s"Found incorrect type $t in model when building trait hierarchy, ignoring")
          innerMap
      }
    }

  def calculateSealedTraits(defns: List[Defn]): Map[String, List[ParamModel]] =
    defns
      .foldLeft(Map.empty[String, List[ParamModel]]) { (map, node) =>
        node match {
          case clazz: Defn.Class =>
            addParentsToSealedTraitMap(clazz.templ.inits, clazz.name.value, map)
          case value: Defn.Trait =>
            addParentsToSealedTraitMap(value.templ.inits, value.name.value, map)
          case defn =>
            println(s"Found incorrect defn type $defn when building trait hierarchy, ignoring")
            map
        }
      }

  def handleSealedTrait(value: Defn.Trait, sealedTraitMap: Map[String, List[ParamModel]]): Model =
    Model(
      value.name.value,
      getGrpcType(Some(Type.Name(value.name.value))),
      sealedTraitMap.getOrElse(value.name.value, List.empty),
      Nil
    )

  def handleCaseClass(clazz: Defn.Class): Model = {
    // flatten implicit params and params because we filter out implicits
    val protoFields = clazz
      .ctor
      .paramss
      .flatten
      // filtering out ignored fields
      .filter { param =>
        !clazz.mods.exists {
          case Mod.Annot(Init(Type.Name(t), _, args)) =>
            t === "srcGenIgnoreField" && args.flatten.exists {
              case Lit.String(ignoredField) => param.name.value === ignoredField
              case _                        => false
            }
          case _ =>
            false
        }
      }
      .map { param =>
        ParamModel(param, getGrpcType(param.decltpe))
      }

    Model(clazz.name.value, getGrpcType(Some(Type.Name(clazz.name.value))), Nil, protoFields)
  }

  def getGrpcScalarType: PartialFunction[Type, String] = {
    case Type.Name("String")  => "string"
    case Type.Name("Int")     => "int32"
    case Type.Name("Long")    => "int64"
    case Type.Name("Float")   => "float32"
    case Type.Name("Double")  => "float64"
    case Type.Name("Boolean") => "bool"
  }

  def getGrpcZonedDateTimeType: PartialFunction[Type, String] = {
    case Type.Name("ZonedDateTime") => "int64"
  }

  def isValidScalarMapType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("Map"), k :: v :: Nil) =>
      isValidMapKeyType(k) && isValidMapValueType(v) && isScalarType(v)
    case _ => false
  }

  def isValidNonScalarMapType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("Map"), k :: v :: Nil) =>
      isValidMapKeyType(k) && isValidMapValueType(v) && !isScalarType(v)
    case _ => false
  }

  def isValidMapKeyType(t: Type): Boolean =
    t match {
      case Type.Name("String" | "Int" | "Long" | "Boolean") => true
      case _                                                => false
    }

  def isValidMapValueType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("List" | "Map"), _) => false
    case _                                        => true
  }

  def getGrpcMapType: PartialFunction[Type, String] = {
    case Type.Apply(Type.Name("Map"), k :: v :: Nil) if isValidMapKeyType(k) && isValidMapValueType(v) =>
      s"map<${getGrpcType(Some(k))}, ${getGrpcType(Some(v))}>"
  }

  def getGrpcListType: PartialFunction[Type, String] = {
    case Type.Apply(Type.Name("List"), inner :: Nil) => s"repeated ${getGrpcType(Some(inner))}"
  }

  def isListScalarType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("List"), inner :: Nil) => isScalarType(inner)
    case _                                           => false
  }

  def isListNonScalarType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("List"), inner :: Nil) => !isScalarType(inner)
    case _                                           => false
  }

  def isListZonedDateTimeType(t: Type): Boolean = t match {
    case Type.Apply(Type.Name("List"), inner :: Nil) => isZonedDateTimeType(inner)
    case _                                           => false
  }

  def isScalarType(t: Type): Boolean =
    getGrpcScalarType
      .andThen(_ => true)
      .orElse[Type, Boolean] {
        case _ =>
          false
      }(t)

  def isZonedDateTimeType(t: Type): Boolean =
    getGrpcZonedDateTimeType
      .andThen(_ => true)
      .orElse[Type, Boolean] {
        case _ =>
          false
      }(t)

  def getGrpcType(t: Option[Type]): String = {

    val Grpc = "Grpc"

    def getGrpcMessageType: PartialFunction[Type, String] = {
      case Type.Name(nonScalar) => s"$Grpc$nonScalar"
    }

    def getGrpcOptionType: PartialFunction[Type, String] = {
      case Type.Apply(Type.Name("Option"), Type.Name(innerType) :: Nil) =>
        s"Maybe$innerType"

      case Type.Apply(Type.Name("Option"), Type.Apply(Type.Name("List"), Type.Name(innerType) :: Nil) :: Nil) =>
        s"MaybeList$innerType"

      case Type.Apply(Type.Name("Option"), Type.Apply(Type.Name("Map"), Type.Name(k) :: Type.Name(v) :: Nil) :: Nil) =>
        s"MaybeMap$k$v"

      case Type.Apply(Type.Name("Option"), head :: Nil) =>
        s"Maybe${getGrpcOptionType(head)}"

    }

    getGrpcOptionType
      .andThen(ot => s"$Grpc$ot")
      .orElse(getGrpcScalarType)
      .orElse(getGrpcZonedDateTimeType)
      .orElse(getGrpcListType)
      .orElse(getGrpcMapType)
      .orElse(getGrpcMessageType)
      .orElse[Type, String] {
        case _ => "Unknown"
      }(t.getOrElse(Type.Name("Unknown")))
  }

}
