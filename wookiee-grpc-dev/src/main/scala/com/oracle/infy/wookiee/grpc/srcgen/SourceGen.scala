package com.oracle.infy.wookiee.grpc.srcgen

import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGen._
import com.oracle.infy.wookiee.grpc.srcgen.GrpcSourceGenRenderScala._
import com.oracle.infy.wookiee.grpc.srcgen.SourceGen.{RPC, RPCType, Service, scalafmt}
import com.oracle.infy.wookiee.grpc.srcgen.SourceGenModel.{Model, ScalaFileSource, ScalaSource, ScalaTextSource}
import com.oracle.infy.wookiee.grpc.srcgen.implicits._
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

    val defns = vfiles
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
          defns
      }

    val sealedTraitMap = calculateSealedTraits(defns)

    val models = defns.flatMap {
      case clazz: Defn.Class =>
        Some(handleCaseClass(clazz))
      case value: Defn.Trait =>
        Some(handleSealedTrait(value, sealedTraitMap))
      case _ => None // not a valid type
    }

    models.sortBy(_.scalaTypeName)
  }

  def genProto(headers: List[String], services: List[Service], sources: List[ScalaSource]): String = {

    val protoHeaders = ("""syntax = "proto3";""" :: headers)
      .mkString("\n")

    def handleStreamingRPCType(rpcType:RPCType): String = {
      if(rpcType.isStreaming) {
        s"stream ${rpcType.name}"
      } else {
        rpcType.name
      }
    }
    def renderRPC(rpc: RPC): String = {
      val rpcInput =handleStreamingRPCType(rpc.input)
      val rpcOutput =handleStreamingRPCType(rpc.output)

      s"""rpc ${rpc.name} ($rpcInput) returns ($rpcOutput) {}"""
    }

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

    val models = getModels(sources)
    val allModels = (models ++ synthesizeOptionModel(models))
      .sortBy(_.scalaTypeName)

    val sep = "// DO NOT EDIT! (this code is generated)"
    s"""
      |$protoHeaders
      |
      |$renderServices
      |${allModels.map(_.renderProto).mkString(s"\n$sep", s"\n$sep", "")}
      |""".stripMargin
  }

  def genScala(headers: List[String], sources: List[ScalaSource]): String = {

    val fmt: String => String = str => scalafmt.format(Paths.get(".scalafmt.conf"), Paths.get("Main.scala"), str)

    val models = getModels(sources)
    val optionalTypes = getOptionalTypes(models).toList.sortBy(_.toString)

    val scalaSource = s"""
       |${headers.mkString("\n")}
       |
       |object implicits {
       |$renderScalaGlobals
       |${models.map(renderScala(_, fmt)).mkString("\n")}
       |${optionalTypes.map(renderScalaOptional(_, fmt)).mkString("\n")}
       |}
       |""".stripMargin

    fmt(scalaSource)
  }
}


object SourceGen {
  final case class Service(name: String, rpcs: List[RPC])

  final case class RPCType(name: String, isStreaming: Boolean)
  final case class RPC(name: String, input: RPCType, output: RPCType)
  private lazy val scalafmt: Scalafmt = Scalafmt.create(this.getClass.getClassLoader)
}
