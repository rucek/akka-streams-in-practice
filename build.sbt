name := "akka-streams-in-practice"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += "Bintray" at "http://dl.bintray.com/websudos/oss-releases"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "com.typesafe.akka" %% "akka-stream" % "2.4.7",
  "com.websudos" %% "phantom-dsl" % "1.22.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.7" % "test"
)