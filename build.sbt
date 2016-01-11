name := """gingging"""
organization := "se.durre"
version := "1.0.0"

scalaVersion := "2.11.7"

// Change this to another test framework if you prefer
libraryDependencies ++= Seq(
  "com.rabbitmq" % "amqp-client" % "3.6.0",
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.1",
  "com.typesafe" % "config" % "1.2.1",
  "com.typesafe.scala-logging" %%  "scala-logging" % "3.1.0",
  "ch.qos.logback"             %   "logback-core"             % "1.1.2",
  "ch.qos.logback"             %   "logback-classic"          % "1.1.2",
  "org.specs2" %% "specs2-core" % "2.4.15" % "test",
  "org.specs2" %% "specs2-mock" % "2.4.15" % "test"
)
