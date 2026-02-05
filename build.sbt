ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "no.marz"
ThisBuild / organizationName := "Marz"
ThisBuild / organizationHomepage := Some(url("https://github.com/razeghi71"))
ThisBuild / description := "Scala 3 toolkit for building AI agents with unified LLM providers and graph-based workflow orchestration"
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("https://github.com/razeghi71/agent4s"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/razeghi71/agent4s"),
    "scm:git@github.com:razeghi71/agent4s.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "razeghi71",
    name = "Razeghi",
    email = "razeghi71@gmail.com",
    url = url("https://github.com/razeghi71")
  )
)
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeRepository := "https://central.sonatype.com/api/v1/publisher"
ThisBuild / versionScheme := Some("early-semver")

val circeVersion = "0.14.9"
val http4sVersion = "0.23.33"
val catsVersion = "2.13.0"
val catsEffectVersion = "3.6.3"
val fs2Version = "3.12.2"
val munitVersion = "1.2.2"

lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion
)

lazy val testDependencies = Seq(
  "org.scalameta" %% "munit" % munitVersion % Test
)

lazy val circeDependencies = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

lazy val http4sDependencies = Seq(
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion
)

lazy val root = (project in file("."))
  .aggregate(llm, graph, commonTools)
  .settings(
    name := "agent4s",
    publish / skip := true
  )

lazy val llm = (project in file("modules/llm"))
  .settings(
    name := "agent4s-llm",
    libraryDependencies ++= commonDependencies ++
      circeDependencies ++
      http4sDependencies ++
      testDependencies
  )

lazy val graph = (project in file("modules/graph"))
  .settings(
    name := "agent4s-graph",
    libraryDependencies ++= commonDependencies ++
      Seq("co.fs2" %% "fs2-core" % fs2Version) ++
      testDependencies
  )

lazy val commonTools = (project in file("modules/common-tools"))
  .dependsOn(llm)
  .settings(
    name := "agent4s-common-tools",
    libraryDependencies ++= commonDependencies ++ testDependencies
  )

lazy val examples = (project in file("modules/examples"))
  .dependsOn(llm, graph, commonTools)
  .settings(
    name := "agent4s-examples",
    libraryDependencies ++= commonDependencies ++ Seq(
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
    ),
    publish / skip := true
  )
