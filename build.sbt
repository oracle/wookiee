import java.io.File

import sbt.Keys._
import sbt._

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
  },
  ciBuildNoTest := {
    (Keys.`package` in Compile).value
    makePom.value
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
    },
    ciBuild := {
      ((Keys.`package` in Compile) dependsOn (test in Compile)).value
      makePom.value
    }
  )
  .dependsOn(
    `wookiee-core`,
    `wookiee-grpc`
  )
  .aggregate(
    `wookiee-core`,
    `wookiee-grpc`
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
    //scalaPB
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    //scalaPB
    mdocIn := file("wookiee-docs/docs"),
    mdocOut := file("."),
    mdocVariables := Map(
      "VERSION" -> version.value.split("-").headOption.getOrElse("error-in-build-sbt"),
      "PROTO_FILE" -> protoFile,
      "PROTO_DEF" -> readF(s"wookiee-docs/$protoFile", _.mkString),
      "PLUGIN_DEF" -> readSection("project/plugins.sbt", "scalaPB"),
      "PROJECT_DEF" -> readSection("build.sbt", "scalaPB"),
      "EXAMPLE" -> readF("wookiee-docs/src/main/scala/com/oracle/infy/wookiee/Example.scala", _.drop(2).mkString)
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      Deps.test.curatorTest,
      Deps.test.slf4jLog4jImpl
    )
  )
  .dependsOn(root)
  .enablePlugins(MdocPlugin)
