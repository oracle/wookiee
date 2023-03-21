addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.13")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")
dependencyOverrides += "ch.epfl.scala" % "scalafix-interfaces" % "0.10.4"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

//scalaPB
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.12"
//scalaPB

//dependency check tool
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "4.2.0")