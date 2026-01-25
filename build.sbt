val scala3Version = "3.7.4"
val circeVersion = "0.14.9"

lazy val root = project
  .in(file("."))
  .settings(
    name := "agent4s",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.1" % Test,
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.6.3",
    libraryDependencies += "io.cequence" %% "openai-scala-client" % "1.3.0.RC.1",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )
