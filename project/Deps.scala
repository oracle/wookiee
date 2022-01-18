import sbt._

object Deps {

  object versions {
    val akkaVersion = "2.6.18"
    val scalaStmVersion = "0.11.1"
    val shapelessVersion = "1.2.3"
    val curatorVersion = "5.1.0"
    val catsVersion = "2.0.0"
    val log4CatsVersion = "1.1.1"
    val circeVersion = "0.12.3"
    val µTestVersion = "0.7.2"
    val scalacheckVersion = "1.15.4"
    val scalatestVersion = "3.2.9"
    val fs2Version = "2.4.0"
    val junitVersion = "4.13.2"
    val guavaVersion = "31.0.1-jre"
    val grpcVersion: String = scalapb.compiler.Version.grpcJavaVersion

    val slf4jVersion = "1.7.33"
    val slf4jImplVersion = "2.17.1"
    val logbackVersion = "1.2.10"
    val jodaTimeVersion = "2.10.13"
    val slf4jApi: ModuleID = "org.slf4j" % "slf4j-api" % slf4jVersion
    val logbackClassic: ModuleID = "ch.qos.logback" % "logback-classic" % logbackVersion
    val jodaTime: ModuleID = "joda-time" % "joda-time" % jodaTimeVersion
    val scalaCollectionCompatVersion = "2.3.0"
    val zookeeperVersion = "3.6.2"
    val json4sVersion = "4.0.3"
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

    val json4sLibs: Seq[ModuleID] = Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ext" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion
    )

    val dropWizardLibs: Seq[ModuleID] = Seq(
      "io.dropwizard.metrics" % "metrics-core" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-graphite" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jvm" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jmx" % dropwizardVersion
    )

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
    val cats: ModuleID = "org.typelevel" %% "cats-core" % catsVersion
    val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % catsVersion
    val guava: ModuleID = "com.google.guava" % "guava" % guavaVersion
    val zookeeper: ModuleID = "org.apache.zookeeper" % "zookeeper" % zookeeperVersion exclude("org.slf4j", "slf4j-log4j12")

    val log4CatsCore: ModuleID = "io.chrisdavenport" %% "log4cats-core" % log4CatsVersion
    val log4CatsSlf4J: ModuleID = "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion

    val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
    val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
    val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion
    val fs2: ModuleID = "co.fs2" %% "fs2-core" % fs2Version
    val grpcNetty: ModuleID = "io.grpc" % "grpc-netty-shaded" % grpcVersion
    val grpcProtoBuf: ModuleID = "io.grpc" % "grpc-protobuf" % grpcVersion
    val grpcStub: ModuleID = "io.grpc" % "grpc-stub" % grpcVersion
    def scalaReflect(scalaVersion: String): ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion

    val scalaCollectionCompat
        : ModuleID = "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion

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

    val all: Seq[ModuleID] = Seq(
      akka,
      catsEffect,
      slf4jApi,
      logbackClassic,
      jodaTime,
      scalaStm,
      guava,
      curator,
      scalaCollectionCompat,
      cats,
      log4CatsCore,
      log4CatsSlf4J,
      circeCore,
      circeParser,
      circeGeneric,
      fs2,
      grpcNetty,
      grpcProtoBuf,
      grpcStub
    )

  }

  object test {

    import versions._

    val scalacheck: ModuleID = "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    val scalatest: ModuleID = "org.scalatest" %% "scalatest" % scalatestVersion % Test
    val akkaTest: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
    val junit: ModuleID = "junit" % "junit" % junitVersion % Test
    val µTest: ModuleID = "com.lihaoyi" %% "utest" % µTestVersion % Test
    val shapeless: ModuleID = "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % shapelessVersion % Test
    val curatorTest: ModuleID = "org.apache.curator" % "curator-test" % curatorVersion
    val akkaStreamTest: ModuleID = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test

    val slf4jLog4jImpl: ModuleID = "org.apache.logging.log4j" % "log4j-slf4j-impl" % slf4jImplVersion
    val log4CatsNoop: ModuleID = "io.chrisdavenport" %% "log4cats-noop" % log4CatsVersion

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
