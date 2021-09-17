addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.12")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.19")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.2.3")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

//scalaPB
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.1"
//scalaPB
