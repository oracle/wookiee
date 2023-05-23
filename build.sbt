import java.io.File
import sbt.Keys.{libraryDependencies, _}
import sbt._

import scala.language.postfixOps
import scala.util.Try

val buildVersion = Try {
  IO.read(new File("VERSION")).trim
}.toOption
  .getOrElse("unknown")

val projectVersion = Option(System.getenv("CI_RELEASE")).getOrElse(s"$buildVersion-SNAPSHOT")

val LatestScalaVersion = "2.13.10"
val Scala212 = "2.12.17"
val ScalaVersions = Seq(LatestScalaVersion, Scala212)

lazy val ciBuild = taskKey[Unit]("prepare final builds")
lazy val ciBuildNoTest = taskKey[Unit]("prepare final builds without tests")
lazy val myTask = taskKey[Unit]("myTask")

addCommandAlias(
  name = "check",
  value = ";scalafmtCheckAll ;compile:scalafix --check ;test:scalafix --check"
)

val org = "com.oracle.infy"
// dependency check options
filterScalaLibrary := true // exclude scala library in output

val commonScalacOptions =
  Seq(
    "-encoding",
    "UTF-8",
    "-Ypatmat-exhaust-depth",
    "off",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-value-discard",
    "-Xlint:-nullary-unit",
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-explaintypes",
    "-unchecked",
    "-language:higherKinds"
  )

def commonSettings(warnUnused: Boolean): Seq[Setting[_]] = Seq(
  Test / parallelExecution := false,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalaVersion := LatestScalaVersion,
  crossScalaVersions := ScalaVersions,
  version := projectVersion,
  organization := org,
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / logBuffered := false,
  Compile / packageDoc / publishArtifact := false,
  ThisBuild / semanticdbVersion := Deps.versions.scalametaVersion,
  semanticdbCompilerPlugin := {
    ("org.scalameta" % "semanticdb-scalac" % semanticdbVersion.value)
      .cross(CrossVersion.full)
  },
  ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
  scalacOptions := scalaVersion.map {
    case `LatestScalaVersion` if warnUnused =>
      commonScalacOptions ++ Seq("-Ywarn-unused")
    case `LatestScalaVersion` =>
      commonScalacOptions
    case _ if warnUnused =>
      commonScalacOptions ++ Seq(
        "-Ypartial-unification",
        "-Xfuture",
        "-Ywarn-adapted-args",
        "-Ywarn-unused"
      )
    case _ =>
      commonScalacOptions ++ Seq(
        "-Ypartial-unification",
        "-Xfuture",
        "-Ywarn-adapted-args"
      )
  }.value,
  compile := ((Compile / compile) dependsOn (Test / compile)).value,
  ciBuild := {
    ((Compile / Keys.`packageSrc`) dependsOn (Test / test)).value
    (Compile / Keys.`package`).value
    makePom.value
  },
  ciBuildNoTest := {
    (Compile / Keys.`package`).value
    makePom.value
  }
)

lazy val `wookiee-core` = project
  .in(file("wookiee-core"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.core ++ Deps.test.all
  )
  .dependsOn(`wookiee-libs`)
  .aggregate(`wookiee-libs`)

lazy val `wookiee-test` = project
  .in(file("wookiee-test"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.core ++ Deps.test.all
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

// For messaging between wookiee services via gRPC
lazy val `wookiee-libs` = project
  .in(file("wookiee-libs"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeLibs,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

// For messaging between wookiee services via gRPC
lazy val `wookiee-discovery` = project
  .in(file("wookiee-discovery"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies += Deps.test.scalatest
  )
  .dependsOn(`wookiee-grpc-component`)
  .aggregate(`wookiee-grpc-component`)

// For HTTP/WS support, use instead of wookiee-akka-http
lazy val `wookiee-helidon` = project
  .in(file("wookiee-helidon"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeHelidon
  )
  .dependsOn(`wookiee-core`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-test`)

lazy val `basic-service` = project
  .in(file("examples/basic-service"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.core ++ Deps.test.all
  )
  .dependsOn(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)
  .aggregate(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)

lazy val `basic-extension` = project
  .in(file("examples/basic-extension"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.core ++ Deps.test.all
  )
  .dependsOn(`wookiee-core`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-test`)

lazy val `metrics-example` = project
  .in(file("examples/metrics"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeMetrics
  )
  .dependsOn(`wookiee-core`, `wookiee-metrics`)
  .aggregate(`wookiee-core`, `wookiee-metrics`)

lazy val `caching-example` = project
  .in(file("examples/caching"))
  .settings(commonSettings(false))
  .dependsOn(`wookiee-core`, `wookiee-cache-memcache`)
  .aggregate(`wookiee-core`, `wookiee-cache-memcache`)

lazy val `wookiee-grpc` = project
  .in(file("wookiee-grpc"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    libraryDependencies ++= Deps.build.wookieeGrpc
  )

lazy val `wookiee-grpc-component` = project
  .in(file("wookiee-grpc-component"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    libraryDependencies ++= Deps.build.wookieeGrpc ++ Seq(
      Deps.test.akkaTest,
      Deps.test.curatorTest,
      Deps.test.scalatest
    ),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
  .dependsOn(`wookiee-core`, `wookiee-grpc`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-grpc`, `wookiee-test`)

lazy val `wookiee-grpc-tests` = project
  .in(file("wookiee-grpc-tests"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    test := {
      (Test / test).value
      (Test / runMain).toTask(" com.oracle.infy.wookiee.grpc.UnitTestConstable").value
      (Test / runMain).toTask(" com.oracle.infy.wookiee.grpc.IntegrationConstable").value
      (Test / runMain).toTask(" com.oracle.infy.wookiee.grpcdev.UnitTestConstable").value
      (Test / runMain).toTask(" com.oracle.infy.wookiee.grpcdev.IntegrationConstable").value
    },
    libraryDependencies ++= Seq(
      Deps.test.curatorTest,
      Deps.test.log4CatsNoop,
      Deps.test.scalacheck,
      Deps.test.scalatest,
      Deps.test.µTest
    )
  )
  .dependsOn(`wookiee-grpc`, `wookiee-grpc-dev`, `wookiee-proto`, `wookiee-health`, `wookiee-functional-metrics`)
  .aggregate(`wookiee-grpc`, `wookiee-grpc-dev`, `wookiee-proto`, `wookiee-health`, `wookiee-functional-metrics`)

lazy val `wookiee-functional-metrics` = project
  .in(file("wookiee-functional-metrics"))
  .settings(commonSettings(true))
  .settings(
    libraryDependencies ++= Deps.build.wookieeFuncMetrics
  )
  .dependsOn(`wookiee-grpc`)
  .aggregate(`wookiee-grpc`)

lazy val `wookiee-http` = project
  .in(file("wookiee-http"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    libraryDependencies ++= Deps.build.http4s
  )
  .dependsOn(`wookiee-grpc`)
  .dependsOn(`wookiee-grpc`)

lazy val `wookiee-health` = project
  .in(file("wookiee-health"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    libraryDependencies ++= Deps.build.circe
  )
  .dependsOn(`wookiee-http`, `wookiee-grpc`)
  .aggregate(`wookiee-http`, `wookiee-grpc`)

lazy val `wookiee-grpc-dev` = project
  .in(file("wookiee-grpc-dev"))
  .settings(commonSettings(true))
  .settings(
    libraryDependencies ++= Seq(
      Deps.build.scalaReflect(scalaVersion.value),
      Deps.build.scalaMeta,
      Deps.build.scalaFmtDyn
    )
  )

lazy val `wookiee-zookeeper` = project
  .in(file("wookiee-zookeeper"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeZk
  )
  .dependsOn(`wookiee-core`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-test`)

lazy val `wookiee-metrics` = project
  .in(file("wookiee-metrics"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeMetrics
  )
  .dependsOn(`wookiee-core`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-test`)

lazy val `wookiee-akka-http` = project
  .in(file("wookiee-akka-http"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeAkkaHttp,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
  .dependsOn(`wookiee-core`, `wookiee-test`, `wookiee-metrics`, `wookiee-libs`)
  .aggregate(`wookiee-core`, `wookiee-test`, `wookiee-metrics`, `wookiee-libs`)

lazy val `wookiee-cache` = project
  .in(file("wookiee-cache"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeCache
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

lazy val `wookiee-cache-memcache` = project
  .in(file("wookiee-cache-memcache"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeMemcache
  )
  .dependsOn(`wookiee-core`, `wookiee-metrics`, `wookiee-cache`)
  .aggregate(`wookiee-core`, `wookiee-metrics`, `wookiee-cache`)

lazy val root = project
  .in(file("."))
  .settings(commonSettings(false))
  .settings(
    name := "wookiee",
    libraryDependencies ++= Deps.test.all,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    test := {
      (Test / test).value
    },
    ciBuild := {
      ((Compile / Keys.`package`) dependsOn (Compile / test)).value
      makePom.value
    }
  )
  .dependsOn(
    `wookiee-libs`,
    `wookiee-core`,
    `wookiee-grpc-dev`,
    `wookiee-grpc-tests`,
    `wookiee-grpc`,
    `wookiee-proto`,
    `wookiee-http`,
    `wookiee-helidon`,
    `wookiee-health`,
    `wookiee-test`,
    `wookiee-zookeeper`,
    `wookiee-grpc-component`,
    `wookiee-metrics`,
    `wookiee-akka-http`,
    `wookiee-cache`,
    `wookiee-cache-memcache`,
    `wookiee-functional-metrics`,
    `wookiee-discovery`
  )
  .aggregate(
    `wookiee-libs`,
    `wookiee-core`,
    `wookiee-grpc-dev`,
    `wookiee-grpc`,
    `wookiee-grpc-tests`,
    `wookiee-proto`,
    `wookiee-health`,
    `wookiee-helidon`,
    `wookiee-akka-http`,
    `wookiee-test`,
    `wookiee-zookeeper`,
    `wookiee-grpc-component`,
    `wookiee-metrics`,
    `wookiee-http`,
    `wookiee-cache`,
    `wookiee-cache-memcache`,
    `wookiee-functional-metrics`,
    `wookiee-discovery`
  )

def readF[A](file: String, func: List[String] => A): A = {
  val src = scala.io.Source.fromFile(file)
  try func(src.getLines().toList.map(_ ++ "\n"))
  finally src.close()
}

def readSection(file: String, section: String): String = {
  val s = s"$section\n"
  readF(file, _.dropWhile(a => !a.endsWith(s)).drop(1).takeWhile(a => !a.endsWith(s)).mkString)
}

val protoFile = "src/main/protobuf/myService.proto"
val exampleFile = "wookiee-docs/src/main/scala/com/oracle/infy/wookiee/Example.scala"

lazy val `wookiee-docs` = project
  .in(file("wookiee-docs"))
  .settings(commonSettings(true))
  .settings(
    scalafixConfig := Some(file(".scalafix_strict.conf")),
    // NOTE: DO NOT use $ in variable value, otherwise mdoc complains
    mdocIn := file("wookiee-docs/docs"),
    mdocOut := file("."),
    mdocVariables := Map(
      "VERSION" -> version.value.split("-").headOption.getOrElse("error-in-build-sbt"),
      "PROTO_FILE" -> protoFile,
      "PROTO_DEF" -> readF(s"wookiee-proto/" ++ protoFile, _.mkString),
      "PLUGIN_DEF" -> readSection("project/plugins.sbt", "scalaPB"),
      "PROJECT_DEF" -> readSection("build.sbt", "scalaPB"),
      "CHANNEL_SETTINGS" -> readSection(exampleFile, "channelSettings"),
      "GRPC_CALL" -> readSection(exampleFile, "grpcCall"),
      "CREATE_SERVER" -> readSection(exampleFile, "Creating a Server"),
      "IMPORTS" -> readSection(exampleFile, "wookiee-grpc imports"),
      "EXAMPLE" -> readF(exampleFile, _.drop(2).mkString)
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      Deps.test.curatorTest,
      Deps.test.slf4jLog4jImpl
    )
  )
  .dependsOn(root, `wookiee-proto`)
  .enablePlugins(MdocPlugin)

lazy val `wookiee-proto` = project
  .in(file("wookiee-proto"))
  .settings(commonSettings(true))
  .settings(
    //scalaPB
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++= Deps.build.wookieeProto
  )
