name := "akka-streams"

version := "0.1"

scalaVersion := "2.12.5"

scalacOptions += "-Ypartial-unification"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.11",
  "com.danielasfregola" %% "twitter4s" % "5.5",
  "org.typelevel" %% "cats-core" % "1.1.0"
)
