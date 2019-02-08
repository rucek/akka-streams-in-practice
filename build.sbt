name := "akka-streams-in-practice"

version := "1.0"

scalaVersion := "2.11.12"

resolvers += "Bintray" at "http://dl.bintray.com/websudos/oss-releases"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "com.typesafe.akka" %% "akka-stream" % "2.5.20",
  "com.websudos" %% "phantom-dsl" % "1.22.0",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.20" % "test"
)