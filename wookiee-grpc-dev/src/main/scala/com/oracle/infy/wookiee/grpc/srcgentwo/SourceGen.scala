package com.oracle.infy.wookiee.grpc.srcgentwo

import com.oracle.infy.wookiee.grpc.srcgentwo.GrpcSourceGen._
import com.oracle.infy.wookiee.grpc.srcgentwo.GrpcSourceGenRenderScala._
import com.oracle.infy.wookiee.grpc.srcgentwo.SourceGenModel.{Model, ScalaFileSource, ScalaSource, ScalaTextSource}
import com.oracle.infy.wookiee.grpc.srcgentwo.SourceGen.{RPC, Service}
import com.oracle.infy.wookiee.grpc.srcgentwo.implicits._
import org.scalafmt.interfaces.Scalafmt

import java.nio.file.Paths
import scala.meta.inputs.Input
import scala.meta.{Defn, Init, Mod, Source, Type}

trait SourceGen {

  private def getModels(sources: List[ScalaSource]): List[Model] = {

    val vfiles = sources.map {
      case source: ScalaFileSource =>
        val p = Paths.get(source.scalaFilePath)
        val src = new String(java.nio.file.Files.readAllBytes(p))
        source -> Input.VirtualFile(p.toFile.getName, src)
      case source: ScalaTextSource =>
        source -> Input.VirtualFile("InlineScala", source.content)
    }

    val models = vfiles
      .map(a => a._1.filter -> a._2.parse[Source].get)
      .flatMap {
        case (filter, source) =>
          val defns = source
            .collect {
              case node: Defn.Trait if filter(node.name.value) => node
              case node: Defn.Class if filter(node.name.value) => node
            }
            .collect {
              case node: Defn.Trait if !node.mods.exists {
                    case Mod.Annot(Init(Type.Name(t), _, _)) => t === "srcGenIgnoreClass"
                    case _                                   => false
                  } =>
                node
              case node: Defn.Class if !node.mods.exists {
                    case Mod.Annot(Init(Type.Name(t), _, _)) => t === "srcGenIgnoreClass"
                    case _                                   => false
                  } =>
                node
            }

          val sealedTraitMap = calculateSealedTraits(defns)

          defns.flatMap {
            case clazz: Defn.Class =>
              Some(handleCaseClass(clazz))
            case value: Defn.Trait =>
              Some(handleSealedTrait(value, sealedTraitMap))
            case _ => None // not a valid type
          }
      }

    (models ++ synthesizeOptionModel(models))
      .sortBy(_.scalaTypeName)
  }

  def genProto(headers: List[String], services: List[Service], sources: List[ScalaSource]): String = {

    val protoHeaders = ("""syntax = "proto3";""" :: headers)
      .mkString("\n")

    def renderRPC(rpc: RPC): String =
      s"""rpc ${rpc.name}(${rpc.input}) returns (${rpc.output}) {}"""

    def renderRPCs(rpcs: List[RPC]): String =
      rpcs
        .map(renderRPC)
        .mkString("  ", "\n  ", "")

    def renderService(service: Service): String =
      s"""
        |service ${service.name} {
        |${renderRPCs(service.rpcs)}
        |}
        |""".stripMargin

    def renderServices: String =
      services
        .map(renderService)
        .mkString("\n")

    val sep = "// DO NOT EDIT! (this code is generated)"
    s"""
      |$protoHeaders
      |
      |$renderServices
      |${getModels(sources).map(_.renderProto).mkString(s"\n$sep", s"\n$sep", "")}
      |""".stripMargin
  }

  def genScala(headers: List[String], sources: List[ScalaSource]): String = {

    val scalafmt: Scalafmt = Scalafmt.create(this.getClass.getClassLoader)
    val fmt: String => String = str => scalafmt.format(Paths.get(".scalafmt.conf"), Paths.get("Main.scala"), str)

    val models = getModels(sources)
    val scalaSource = s"""
       |${headers.mkString("\n")}
       |
       |object implicits {
       |$renderScalaGlobals
       |${models.map(renderScala(_, fmt)).mkString("\n")}
       |${getOptionalTypes(models).map(renderScalaOptional(_, fmt)).mkString("\n")}
       |}
       |""".stripMargin

    fmt(scalaSource)
  }
}

object SourceGen {
  final case class Service(name: String, rpcs: List[RPC])
  final case class RPC(name: String, input: String, output: String)
}
