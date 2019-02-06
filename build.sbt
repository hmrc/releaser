enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)

val appName = "releaser"

scalaVersion := "2.11.8"

majorVersion := 1

makePublicallyAvailableOnBintray := true

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.5.19",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "commons-io" % "commons-io" % "2.4",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "org.apache.commons" % "commons-compress" % "1.10",
  // force dependencies due to security flaws found in jackson-databind < 2.9.x using XRay
  "com.fasterxml.jackson.core"     % "jackson-core"            % "2.9.7",
  "com.fasterxml.jackson.core"     % "jackson-databind"        % "2.9.7",
  "com.fasterxml.jackson.core"     % "jackson-annotations"     % "2.9.7",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"   % "2.9.7",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.9.7",
  // force dependencies due to security flaws found in xercesImpl 2.11.0
  "xerces" % "xercesImpl" % "2.12.0",
  
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.pegdown" % "pegdown" % "1.6.0" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

resolvers += Resolver.typesafeRepo("releases")

AssemblySettings()

addArtifact(artifact in (Compile, assembly), assembly)
