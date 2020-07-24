import sbt._

object Deps {

  object versions {
    val shapelessVersion = "1.2.3"
    val curatorVersion = "5.1.0"
    val catsVersion = "2.0.0"
    val log4CatsVersion = "1.1.1"
    val circeVersion = "0.12.3"
    val µTestVersion = "0.7.2"
    val scalacheckVersion = "1.14.1"
    val fs2Version = "2.4.0"
    val grpcVersion = "1.27.2"

    val slf4jVersion = "1.7.5"
    val slf4jImplVersion = "2.13.3"
    val scalaCollectionCompatVersion = "2.1.6"
  }

  object build {

    import versions._

    val curator: ModuleID = "org.apache.curator" % "curator-recipes" % curatorVersion

    val cats: ModuleID = "org.typelevel" %% "cats-core" % catsVersion
    val catsEffect: ModuleID = "org.typelevel" %% "cats-effect" % catsVersion

    val log4CatsCore: ModuleID = "io.chrisdavenport" %% "log4cats-core" % log4CatsVersion
    val log4CatsSlf4J: ModuleID = "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion

    val circeCore: ModuleID = "io.circe" %% "circe-core" % circeVersion
    val circeParser: ModuleID = "io.circe" %% "circe-parser" % circeVersion
    val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion
    val fs2: ModuleID = "co.fs2" %% "fs2-core" % fs2Version
    val grpcNetty: ModuleID = "io.grpc" % "grpc-netty-shaded" % grpcVersion
    val grpcProtoBuf: ModuleID = "io.grpc" % "grpc-protobuf" % grpcVersion
    val grpcStub: ModuleID = "io.grpc" % "grpc-stub" % grpcVersion

    val scalaCollectionCompat
        : ModuleID = "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion

    val all: Seq[ModuleID] = Seq(
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
    val µTest: ModuleID = "com.lihaoyi" %% "utest" % µTestVersion % Test
    val shapeless: ModuleID = "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % shapelessVersion % Test
    val curatorTest: ModuleID = "org.apache.curator" % "curator-test" % curatorVersion

//    val slf4jAPI: ModuleID = "org.slf4j" % "slf4j-api" % slf4jVersion
//    val slf4jLog4j: ModuleID = "org.slf4j" % "slf4j-log4j12" % slf4jVersion
    val slf4jLog4jImpl: ModuleID = "org.apache.logging.log4j" % "log4j-slf4j-impl" % slf4jImplVersion

    //val slf4jSimple: ModuleID = "org.slf4j" % "slf4j-simple" % slf4jVersion

    val all: Seq[ModuleID] = Seq(
      slf4jLog4jImpl,
      scalacheck,
      µTest,
      curatorTest,
      shapeless
    )
  }

}
