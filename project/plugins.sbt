addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.12")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.2" )
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

//scalaPB
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.10"
//scalaPB
