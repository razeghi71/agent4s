val scala3Version = "3.7.4"
val circeVersion = "0.14.9"
val http4sVersion = "0.23.33"
val catsVersion = "2.13.0"
val catsEffectVersion = "3.6.3"
val fs2Version = "3.12.2"
val munitVersion = "1.2.2"

// Common dependencies
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

// Root project - aggregates all modules but doesn't publish itself
lazy val root = (project in file("."))
  .aggregate(llm, graph, commonTools)
  .settings(
    name := "agent4s",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    publish / skip := true
  )

// Module 1: LLM - Interact with backend LLMs
lazy val llm = (project in file("modules/llm"))
  .settings(
    name := "agent4s-llm",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ 
                            circeDependencies ++ 
                            http4sDependencies ++ 
                            testDependencies
  )

// Module 2: Graph - Agent orchestration with graph execution
lazy val graph = (project in file("modules/graph"))
  .settings(
    name := "agent4s-graph",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ 
                            Seq("co.fs2" %% "fs2-core" % fs2Version) ++
                            testDependencies
  )

// Module 3: Common Tools - Optional pre-built tools (depends on llm)
lazy val commonTools = (project in file("modules/common-tools"))
  .dependsOn(llm)
  .settings(
    name := "agent4s-common-tools",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ testDependencies
  )

// Examples module - demonstrates usage (depends on all modules)
lazy val examples = (project in file("modules/examples"))
  .dependsOn(llm, graph, commonTools)
  .settings(
    name := "agent4s-examples",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ Seq(
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
    ),
    publish / skip := true
  )
