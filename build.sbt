import sbt.Keys.{libraryDependencies, _}
import sbt._

import java.io.File
import scala.language.postfixOps
import scala.util.Try

val buildVersion = Try {
  IO.read(new File("VERSION")).trim
}.toOption
  .getOrElse("unknown")

val projectVersion = Option(System.getenv("CI_RELEASE")).getOrElse(s"$buildVersion-SNAPSHOT")

val LatestScalaVersion = "2.13.3"
val Scala212 = "2.12.12"
val ScalaVersions = Seq(LatestScalaVersion, Scala212)

lazy val ciBuild = taskKey[Unit]("prepare final builds")
lazy val ciBuildNoTest = taskKey[Unit]("prepare final builds without tests")
lazy val myTask = taskKey[Unit]("myTask")

addCommandAlias(
  name = "check",
  value = ";scalafmtCheckAll ;compile:scalafix --check ;test:scalafix --check"
)

val org = "com.oracle.infy.wookiee"

val commonScalacOptions =
  Seq(
    "-encoding",
    "UTF-8",
    "-Ypatmat-exhaust-depth",
    "off",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-value-discard",
    "-Xlint:-nullary-unit",
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-explaintypes",
    "-unchecked",
    "-language:higherKinds"
  )

val commonSettings: Seq[Setting[_]] = Seq(
  parallelExecution in Test := false,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalaVersion := LatestScalaVersion,
  crossScalaVersions := ScalaVersions,
  version := projectVersion,
  organization := org,
  logBuffered in Test := false,
  publishArtifact in (Compile, packageDoc) := false,
  addCompilerPlugin(scalafixSemanticdb),
  scalafixDependencies in ThisBuild += "com.github.vovapolu" %% "scaluzzi" % "0.1.12",
  scalacOptions := scalaVersion.map {
    case `LatestScalaVersion` => commonScalacOptions
    case _ =>
      commonScalacOptions ++ Seq(
        "-Ypartial-unification",
        "-Xfuture",
        "-Ywarn-adapted-args"
      )
  }.value,
  compile := ((compile in Compile) dependsOn (compile in Test)).value,
  ciBuild := {
    ((Keys.`packageSrc` in Compile) dependsOn (test in Test)).value
    makePom.value
    ()
  },
  ciBuildNoTest := {
    (Keys.`package` in Compile).value
    makePom.value
    ()
  }
)

lazy val `wookiee-core` = project
  .in(file("wookiee-core"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Deps.build.cats,
      Deps.build.catsEffect
    )
  )

lazy val `wookiee-grpc` = project
  .in(file("wookiee-grpc"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Deps.build.all
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

lazy val `wookiee-grpc-dev` = project
  .in(file("wookiee-grpc-dev"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Deps.build.scalaReflect(scalaVersion.value),
      "org.scalameta" %% "scalameta" % "4.4.25",
      "org.scalameta" %% "scalafmt-dynamic" % "3.0.0-RC6"
    )
  )

lazy val `wookiee-http` = project
  .in(file("wookiee-http"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Deps.build.http4s
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

lazy val `wookiee-health` = project
  .in(file("wookiee-health"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Deps.build.circeCore,
      Deps.build.circeGeneric,
      Deps.build.circeParser
    )
  )
  .dependsOn(`wookiee-http`)
  .aggregate(`wookiee-http`)

lazy val `wookiee-metrics` = project
  .in(file("wookiee-metrics"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "io.dropwizard.metrics" % "metrics-core" % Deps.versions.dropwizardMetricsVersion,
      "io.dropwizard.metrics" % "metrics-json" % Deps.versions.dropwizardMetricsVersion,
      "io.dropwizard.metrics" % "metrics-jvm" % Deps.versions.dropwizardMetricsVersion,
      "io.dropwizard.metrics" % "metrics-graphite" % Deps.versions.dropwizardMetricsVersion,
      "io.dropwizard.metrics" % "metrics-jmx" % Deps.versions.dropwizardMetricsVersion,
      Deps.build.scalaCollectionCompat,
      Deps.build.circeCore,
      Deps.build.circeGeneric,
      Deps.build.circeParser
    )
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

lazy val root = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "wookiee",
    libraryDependencies ++= Deps.test.all,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    test := {
      (test in Test).value
      (runMain in Test).toTask(" com.oracle.infy.wookiee.grpc.UnitTestConstable").value
      (runMain in Test).toTask(" com.oracle.infy.wookiee.grpc.IntegrationConstable").value
      (runMain in Test).toTask(" com.oracle.infy.wookiee.grpcdev.UnitTestConstable").value
      (runMain in Test).toTask(" com.oracle.infy.wookiee.grpcdev.IntegrationConstable").value
    },
    ciBuild := {
      ((Keys.`package` in Compile) dependsOn (test in Compile)).value
      makePom.value
      ()
    }
  )
  .dependsOn(
    `wookiee-core`,
    `wookiee-grpc-dev`,
    `wookiee-grpc`,
    `wookiee-proto`,
    `wookiee-http`,
    `wookiee-health`,
    `wookiee-metrics`
  )
  .aggregate(
    `wookiee-core`,
    `wookiee-grpc-dev`,
    `wookiee-grpc`,
    `wookiee-proto`,
    `wookiee-http`,
    `wookiee-health`,
    `wookiee-metrics`
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

lazy val `wookiee-docs` = project
  .in(file("wookiee-docs"))
  .settings(commonSettings)
  .settings(
    // NOTE: DO NOT use $ in variable value, otherwise mdoc complains
    mdocIn := file("wookiee-docs/docs"),
    mdocOut := file("."),
    mdocVariables := Map(
      "VERSION" -> version.value.split("-").headOption.getOrElse("error-in-build-sbt"),
      "PROTO_FILE" -> protoFile,
      "PROTO_DEF" -> readF(s"wookiee-proto/" ++ protoFile, _.mkString),
      "PLUGIN_DEF" -> readSection("project/plugins.sbt", "scalaPB"),
      "PROJECT_DEF" -> readSection("build.sbt", "scalaPB"),
      "EXAMPLE" -> readF("wookiee-docs/src/main/scala/com/oracle/infy/wookiee/Example.scala", _.drop(2).mkString),
      "METRICSEXAMPLE" -> readF(
        "wookiee-docs/src/main/scala/com/oracle/infy/wookiee/MetricsExample.scala",
        _.drop(2).mkString
      )
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
  .settings(commonSettings)
  .settings(
    //scalaPB
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    )
  )
