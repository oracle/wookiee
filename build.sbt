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

val LatestScalaVersion = "2.11.12"
val Scala212 = "2.12.15"
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
    "-language:existentials",
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
  ThisBuild / semanticdbVersion := "4.7.0",
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

lazy val `wookiee-test` = project
  .in(file("wookiee-test"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.core ++ Deps.test.all
  )
  .dependsOn(`wookiee-core`)
  .aggregate(`wookiee-core`)

//lazy val `basic-service` = project
//  .in(file("examples/basic-service"))
//  .settings(commonSettings(false))
//  .settings(
//    libraryDependencies ++= Deps.build.core ++ Deps.test.all
//  )
//  .dependsOn(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)
//  .aggregate(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)

//lazy val `basic-extension` = project
//  .in(file("examples/basic-extension"))
//  .settings(commonSettings(false))
//  .settings(
//    libraryDependencies ++= Deps.build.core ++ Deps.test.all
//  )
//  .dependsOn(`wookiee-core`, `wookiee-test`)
//  .aggregate(`wookiee-core`, `wookiee-test`)

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

//lazy val `wookiee-zookeeper` = project
//  .in(file("wookiee-zookeeper"))
//  .settings(commonSettings(false))
//  .settings(
//    libraryDependencies ++= Deps.build.wookieeZk
//  )
//  .dependsOn(`wookiee-core`, `wookiee-test`)
//  .aggregate(`wookiee-core`, `wookiee-test`)

lazy val `wookiee-metrics` = project
  .in(file("wookiee-metrics"))
  .settings(commonSettings(false))
  .settings(
    libraryDependencies ++= Deps.build.wookieeMetrics
  )
  .dependsOn(`wookiee-core`, `wookiee-test`)
  .aggregate(`wookiee-core`, `wookiee-test`)

//lazy val `wookiee-akka-http` = project
//  .in(file("wookiee-akka-http"))
//  .settings(commonSettings(false))
//  .settings(
//    libraryDependencies ++= Deps.build.wookieeAkkaHttp
//  )
//  .dependsOn(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)
//  .aggregate(`wookiee-core`, `wookiee-test`, `wookiee-metrics`)

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
    `wookiee-core`,
    `wookiee-test`,
//    `wookiee-zookeeper`,
    `wookiee-metrics`,
//    `wookiee-akka-http`,
    `wookiee-cache`,
    `wookiee-cache-memcache`
  )
  .aggregate(
    `wookiee-core`,
//    `wookiee-akka-http`,
    `wookiee-test`,
//    `wookiee-zookeeper`,
    `wookiee-metrics`,
    `wookiee-cache`,
    `wookiee-cache-memcache`
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
