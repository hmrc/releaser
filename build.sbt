enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)

val appName = "releaser"

scalaVersion := "2.11.8"

majorVersion := 1

makePublicallyAvailableOnBintray := true

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.3.8",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "commons-io" % "commons-io" % "2.4",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "org.apache.commons" % "commons-compress" % "1.10",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.pegdown" % "pegdown" % "1.6.0" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

resolvers += Resolver.typesafeRepo("releases")

AssemblySettings()

addArtifact(artifact in (Compile, assembly), assembly)
