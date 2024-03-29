import sbt._

object Deps {

  object versions {
    val akkaVersion = "2.6.20"
    val scalaStmVersion = "0.11.1"
    val shapelessVersion = "1.3.0"
    val scalametaVersion = "4.8.12"
    val scalafmtDynVersion = "3.7.15"

    val curatorVersion = "5.5.0"
    val catsVersion = "2.9.0"
    val catsEffectVersion = "3.4.8"
    // Provides jawn-parser 1.3.2
    val circeVersion = "0.14.5"
    val µTestVersion = "0.8.1"
    val scalacheckVersion = "1.17.0"
    val scalatestVersion = "3.2.15"
    val fs2Version = "3.6.1"
    val junitVersion = "4.13.2"
    val guavaVersion = "32.1.1-jre"
    val finagleVersion = "23.11.0"
    // This comes in from finagle but we want a higher version with less vulnerabilities
    val snakeYamlVersion = "2.2"
    val upickleVersion = "2.0.0"
    val grpcVersion: String = "1.59.1"
    val nettyVersion: String = "4.1.100.Final"
    val nettyTCVersion: String = "2.0.62.Final"
    val scalaPbRuntimeVersion: String = "0.11.14"

    val helidonVersion = "2.6.4"
    val kafkaVersion = "3.5.1"
    val scalaCompatVersion = "1.0.2"

    val slf4jVersion = "2.0.7"
    val logbackVersion = "1.3.6"
    val jodaTimeVersion = "2.12.2"
    val scalaCollectionCompatVersion = "2.9.0"
    val zookeeperVersion = "3.8.3"
    val typesafeVersion = "1.4.2"
    val json4sVersion = "4.0.6"
    val jacksonVersion = "2.14.2"
    val http4sVersion = "0.23.18"
    val http4sBlazeVersion = "0.23.14"
    val dropwizardVersion = "4.2.19"
    val tyrusVersion = "1.20"
    val mockitoVersion = "5.3.1"
  }

  object build {

    import versions._

    val zookeeper
        : ModuleID = "org.apache.zookeeper" % "zookeeper" % zookeeperVersion exclude ("org.slf4j", "slf4j-log4j12")

    // https://mvnrepository.com/artifact/com.typesafe/config
    val typesafe: ModuleID = "com.typesafe" % "config" % typesafeVersion

    val curator
        : ModuleID = "org.apache.curator" % "curator-recipes" % curatorVersion exclude ("org.apache.zookeeper", "zookeeper")
    val nettyAll: ModuleID = "io.netty" % "netty-all" % nettyVersion
    val nettyTC: ModuleID = "io.netty" % "netty-tcnative" % nettyTCVersion

    val tyrus: Seq[ModuleID] = Seq(
      "org.glassfish.tyrus.ext" % "tyrus-extension-deflate" % tyrusVersion
        exclude ("javax.websocket", "javax.websocket-api"),
      "org.glassfish.tyrus" % "tyrus-server" % tyrusVersion
        exclude ("javax.websocket", "javax.websocket-api"),
      "org.glassfish.tyrus" % "tyrus-spi" % tyrusVersion
        exclude ("javax.websocket", "javax.websocket-api"),
      "org.glassfish.tyrus" % "tyrus-core" % tyrusVersion
        exclude ("javax.websocket", "javax.websocket-api")
    )

    val curatorLibs: Seq[ModuleID] = Seq(
      nettyAll,
      nettyTC,
      curator,
      zookeeper,
      "org.apache.curator" % "curator-framework" % curatorVersion exclude ("org.apache.zookeeper", "zookeeper"),
      "org.apache.curator" % "curator-x-discovery" % curatorVersion exclude ("org.apache.zookeeper", "zookeeper"),
      test.curatorTest exclude ("org.apache.zookeeper", "zookeeper")
    )

    val slf4jApi: ModuleID = "org.slf4j" % "slf4j-api" % slf4jVersion
    val julToSlf4j: ModuleID = "org.slf4j" % "jul-to-slf4j" % slf4jVersion
    val jodaTime: ModuleID = "joda-time" % "joda-time" % jodaTimeVersion
    val jacksonDatabind: ModuleID = "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion

    val json4sLibs: Seq[ModuleID] = Seq(
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ext" % json4sVersion,
      "org.json4s" %% "json4s-core" % json4sVersion,
      jacksonDatabind
    )

    val dropWizardLibs: Seq[ModuleID] = Seq(
      "io.dropwizard.metrics" % "metrics-core" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-graphite" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jvm" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-json" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jmx" % dropwizardVersion
    )

    val logbackClassic: ModuleID = "ch.qos.logback" % "logback-classic" % logbackVersion
    val logbackCore: ModuleID = "ch.qos.logback" % "logback-core" % logbackVersion

    val akka
        : ModuleID = "com.typesafe.akka" %% "akka-actor" % akkaVersion exclude ("org.scala-lang.modules", "scala-java8-compat_2.12") exclude ("org.scala-lang.modules", "scala-java8-compat_2.13")

    val scalaMeta: ModuleID = "org.scalameta" %% "scalameta" % scalametaVersion
    val scalaFmtDyn: ModuleID = "org.scalameta" %% "scalafmt-dynamic" % scalafmtDynVersion
    val scalaStm: ModuleID = "org.scala-stm" %% "scala-stm" % scalaStmVersion
    val catsCore: ModuleID = "org.typelevel" %% "cats-core" % catsVersion
    val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % catsEffectVersion
    val catsEffectStd: ModuleID = "org.typelevel" %% "cats-effect-std" % catsEffectVersion
    val catsKernel: ModuleID = "org.typelevel" %% "cats-kernel" % catsVersion

    val cats: Seq[ModuleID] = Seq(
      catsCore,
      catsEffect,
      catsKernel,
      catsEffectStd
    )

    val guava: ModuleID = "com.google.guava" % "guava" % guavaVersion

    val finagle: ModuleID = "com.twitter" %% "finagle-memcached" % finagleVersion
    val snakeYaml: ModuleID = "org.yaml" % "snakeyaml" % snakeYamlVersion
    val upickle: ModuleID = "com.lihaoyi" %% "upickle" % upickleVersion

    val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
    val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
    val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion
    val fs2: ModuleID = "co.fs2" %% "fs2-core" % fs2Version
    val grpcNetty: ModuleID = "io.grpc" % "grpc-netty-shaded" % grpcVersion
    val grpcProtoBuf: ModuleID = "io.grpc" % "grpc-protobuf" % grpcVersion
    val grpcStub: ModuleID = "io.grpc" % "grpc-stub" % grpcVersion
    val grpcAll: ModuleID = "io.grpc" % "grpc-all" % grpcVersion
    val scalaPbRuntime: ModuleID = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalaPbRuntimeVersion

    val http4sServer: ModuleID = "org.http4s" %% "http4s-blaze-server" % http4sBlazeVersion
    val http4sDsl: ModuleID = "org.http4s" %% "http4s-dsl" % http4sVersion
    val htt4sCirce: ModuleID = "org.http4s" %% "http4s-circe" % http4sVersion

    val helidon: Seq[ModuleID] = Seq(
      "io.helidon.webserver" % "helidon-webserver" % helidonVersion,
      "io.helidon.webserver" % "helidon-webserver-tyrus" % helidonVersion,
      "io.helidon.webserver" % "helidon-webserver-cors" % helidonVersion,
      "io.helidon.webclient" % "helidon-webclient" % helidonVersion,
      "io.helidon.logging" % "helidon-logging-slf4j" % helidonVersion,
      "io.helidon.config" % "helidon-config" % helidonVersion,
      "io.helidon.common" % "helidon-common-reactive" % helidonVersion
    ).map(
      _ exclude("javax.websocket", "javax.websocket-api")
    )

    val logging: Seq[ModuleID] = Seq(
      slf4jApi,
      julToSlf4j,
      logbackClassic,
      logbackCore
    )

    val kafkaClient: ModuleID = "org.apache.kafka" % "kafka-clients" % kafkaVersion
    val kafka: ModuleID = "org.apache.kafka" %% "kafka" % kafkaVersion
    val scalaCompat = "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatVersion

    val grpc: Seq[ModuleID] = Seq(
      grpcNetty,
      grpcProtoBuf,
      grpcStub
    )

    val http4s: Seq[ModuleID] = Seq(
      http4sServer,
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

    val wookieeWeb: Seq[ModuleID] = Seq(
      test.scalatest
    ) ++ helidon ++ tyrus ++ json4sLibs

    val wookieeKafka: Seq[ModuleID] = Seq(
      slf4jApi,
      test.scalatest,
      test.curatorTest,
      scalaCompat,
      kafkaClient,
      kafka,
      logbackClassic % Test
    ) ++ json4sLibs

    val wookieeLibs: Seq[ModuleID] = logging ++ Seq(
      typesafe,
      scalaCollectionCompat,
      test.scalatest
    ) ++ curatorLibs ++ cats ++ json4sLibs

    val wookieeMetrics: Seq[ModuleID] = Seq(
      test.scalatest,
      test.akkaTest
    ) ++ json4sLibs ++ dropWizardLibs

    val wookieeZk: Seq[ModuleID] = curatorLibs ++ Seq(
      guava,
      test.scalatest,
      test.akkaTest
    ) ++ json4sLibs

    val wookieeMemcache: Seq[ModuleID] = Seq(
      test.scalatest,
      finagle,
      snakeYaml,
      upickle
    )

    val wookieeGrpc: Seq[ModuleID] = curatorLibs ++ Seq(
      scalaCollectionCompat,
      fs2,
      jacksonDatabind
    ) ++ circe ++ cats ++ grpc

    val wookieeCache: Seq[ModuleID] = Seq(
      test.scalatest
    ) ++ json4sLibs

    val wookieeProto: Seq[ModuleID] = Seq(
      nettyAll,
      nettyTC,
      scalaPbRuntime
    )

    val core: Seq[ModuleID] = logging ++ curatorLibs ++ Seq(
      scalaCompat,
      akka,
      jodaTime,
      scalaStm,
      guava,
      scalaCollectionCompat,
      fs2,
      jacksonDatabind
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

    val all: Seq[ModuleID] = Seq(
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
