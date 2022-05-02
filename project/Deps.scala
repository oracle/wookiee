import sbt._

object Deps {

  object versions {
    val akkaVersion = "2.6.18"
    val scalaStmVersion = "0.11.1"
    val shapelessVersion = "1.3.0"
    val curatorVersion = "5.2.0"
    val catsVersion = "2.7.0"
    val catsEffectVersion = "3.3.11"
    val log4CatsVersion = "2.3.0"
    val circeVersion = "0.14.1"
    val µTestVersion = "0.7.2"
    val scalacheckVersion = "1.15.4"
    val scalatestVersion = "3.2.9"
    val fs2Version = "3.2.7"
    val junitVersion = "4.13.2"
    val guavaVersion = "31.0.1-jre"
    val finagleVersion = "22.1.0"
    val upickleVersion = "1.5.0"
    val grpcVersion: String = scalapb.compiler.Version.grpcJavaVersion

    val slf4jVersion = "1.7.33"
    val slf4jImplVersion = "2.17.1"
    val logbackVersion = "1.2.10"
    val jodaTimeVersion = "2.10.13"
    val scalaCollectionCompatVersion = "2.3.0"
    val zookeeperVersion = "3.8.0"
    val json4sVersion = "4.0.3"
    val http4sVersion = "0.23.11"
    val dropwizardVersion = "4.2.7"
    val akkaHttpVersion = "10.2.7"
    val akkaHttpJson4sVersion = "1.39.2"
    val akkaHttpCorsVersion = "1.1.2"
  }

  object build {

    import versions._

    val curator: ModuleID = "org.apache.curator" % "curator-recipes" % curatorVersion
    val curatorLibs: Seq[ModuleID] = Seq(
      curator exclude("org.apache.zookeeper", "zookeeper"),
      "org.apache.curator" % "curator-framework" % curatorVersion exclude("org.apache.zookeeper", "zookeeper"),
      "org.apache.curator" % "curator-x-discovery" % curatorVersion exclude("org.apache.zookeeper", "zookeeper"),
      test.curatorTest exclude("org.apache.zookeeper", "zookeeper"),
    )

    val slf4jApi: ModuleID = "org.slf4j" % "slf4j-api" % slf4jVersion
    val jodaTime: ModuleID = "joda-time" % "joda-time" % jodaTimeVersion
    val json4sLibs: Seq[ModuleID] = Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ext" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion
    )

    val dropWizardLibs: Seq[ModuleID] = Seq(
      "io.dropwizard.metrics" % "metrics-core" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-graphite" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jvm" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-json" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jmx" % dropwizardVersion
    )

    val logbackClassic: ModuleID = "ch.qos.logback" % "logback-classic" % logbackVersion
    val akka: ModuleID = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaHttp: Seq[ModuleID] = Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-json4s" % akkaHttpJson4sVersion,
      "ch.megard" %% "akka-http-cors" % akkaHttpCorsVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      logbackClassic
    )

    val scalaStm: ModuleID = "org.scala-stm" %% "scala-stm" % scalaStmVersion
    val catsCore: ModuleID = "org.typelevel" %% "cats-core" % catsVersion
    val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % catsEffectVersion
    val catsKernel: ModuleID = "org.typelevel" %% "cats-kernel" % catsVersion

    val cats: Seq[ModuleID] = Seq(
      catsCore,
      catsEffect,
      catsKernel
    )

    val guava: ModuleID = "com.google.guava" % "guava" % guavaVersion
    val zookeeper: ModuleID = "org.apache.zookeeper" % "zookeeper" % zookeeperVersion exclude("org.slf4j", "slf4j-log4j12")

    val log4CatsCore: ModuleID = "org.typelevel" %% "log4cats-core" % log4CatsVersion
    val log4CatsSlf4J: ModuleID = "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion

    val finagle: ModuleID = "com.twitter" %% "finagle-memcached" % finagleVersion
    val upickle: ModuleID = "com.lihaoyi" %% "upickle" % upickleVersion

    val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
    val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
    val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion
    val fs2: ModuleID = "co.fs2" %% "fs2-core" % fs2Version
    val grpcNetty: ModuleID = "io.grpc" % "grpc-netty-shaded" % grpcVersion
    val grpcProtoBuf: ModuleID = "io.grpc" % "grpc-protobuf" % grpcVersion
    val grpcStub: ModuleID = "io.grpc" % "grpc-stub" % grpcVersion

    val http4sServer: ModuleID = "org.http4s" %% "http4s-blaze-server" % http4sVersion
    val http4sClient: ModuleID = "org.http4s" %% "http4s-async-http-client" % http4sVersion
    val http4sDsl: ModuleID = "org.http4s" %% "http4s-dsl" % http4sVersion
    val htt4sCirce: ModuleID = "org.http4s" %% "http4s-circe" % http4sVersion

    val grpc: Seq[ModuleID] = Seq(
      grpcNetty,
      grpcProtoBuf,
      grpcStub
    )

    val http4s: Seq[ModuleID] = Seq(
      http4sServer,
      http4sClient,
      http4sDsl,
      htt4sCirce
    )

    val circe: Seq[ModuleID] = Seq(
      circeCore,
      circeGeneric,
      circeParser
    )
    def scalaReflect(scalaVersion: String): ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion

    val scalaCollectionCompat
        : ModuleID = "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion

    val wookieeFuncMetrics: Seq[ModuleID] = Seq(scalaCollectionCompat) ++ circe ++ dropWizardLibs

    val wookieeAkkaHttp: Seq[ModuleID] = Seq(
      test.scalatest,
      test.akkaTest,
      akkaStream,
      test.akkaStreamTest
    ) ++ json4sLibs ++ akkaHttp

    val wookieeMetrics: Seq[ModuleID] = Seq(
      test.scalatest,
      test.akkaTest
    ) ++ json4sLibs ++ dropWizardLibs

    val wookieeZk: Seq[ModuleID] = Seq(
      zookeeper,
      guava,
      test.scalatest,
      test.curatorTest,
      test.akkaTest
    ) ++ json4sLibs ++ curatorLibs

    val wookieeMemcache: Seq[ModuleID] = Seq(
      test.scalatest,
      finagle,
      upickle
    )

    val wookieeGrpc: Seq[ModuleID] = Seq(
      curator,
      scalaCollectionCompat,
      log4CatsCore,
      log4CatsSlf4J,
      fs2
    ) ++ circe ++ cats ++ grpc

    val wookieeCache: Seq[ModuleID] = Seq(
      test.scalatest,
      test.akkaTest
    ) ++ json4sLibs

    val core: Seq[ModuleID] = Seq(
      akka,
      slf4jApi,
      logbackClassic,
      jodaTime,
      scalaStm,
      guava,
      curator,
      scalaCollectionCompat,
      fs2
    ) ++ circe ++ cats
  }

  object test {

    import versions._

    val scalacheck: ModuleID = "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    val scalatest: ModuleID = "org.scalatest" %% "scalatest" % scalatestVersion % Test
    val akkaTest: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
    val junit: ModuleID = "junit" % "junit" % junitVersion % Test
    val µTest: ModuleID = "com.lihaoyi" %% "utest" % µTestVersion % Test
    val shapeless: ModuleID = "com.github.alexarchambault" %% "scalacheck-shapeless_1.15" % shapelessVersion % Test
    val curatorTest: ModuleID = "org.apache.curator" % "curator-test" % curatorVersion
    val akkaStreamTest: ModuleID = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test

    val slf4jLog4jImpl: ModuleID = "org.apache.logging.log4j" % "log4j-slf4j-impl" % slf4jImplVersion % Test
    val log4CatsNoop: ModuleID = "org.typelevel" %% "log4cats-noop" % log4CatsVersion % Test

    val all: Seq[ModuleID] = Seq(
      log4CatsNoop,
      slf4jLog4jImpl,
      scalacheck,
      scalatest,
      akkaTest,
      junit,
      µTest,
      curatorTest,
      shapeless
    )
  }

}
