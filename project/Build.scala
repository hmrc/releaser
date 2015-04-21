/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.DefaultBuildSettings._


object HmrcBuild extends Build {

  val appName = "releaser"
  val appVersion = "0.1.0"

  val libraries = Seq(
    "com.typesafe.play" %% "play-ws" % "2.3.8",
    "com.jsuereth" %% "scala-arm" % "1.4",
    "org.scalatest" %% "scalatest" % "2.2.2" % "test",
    "org.pegdown" % "pegdown" % "1.4.2" % "test"
  )

  lazy val sbtUtils = Project(appName, file("."))
    .enablePlugins(SbtAutoBuildPlugin)
    .settings(
      version := appVersion,
      targetJvm := "jvm-1.7",
      scalaVersion := "2.11.6",
      libraryDependencies ++= libraries,
      BuildDescriptionSettings(),
      assemblyMergeStrategy in assembly := {
        case PathList("org", "apache", "commons", "logging", xs@_*) => MergeStrategy.first
        case PathList("play", "core", "server", xs@_*) => MergeStrategy.first
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      }
    )
}


object BuildDescriptionSettings {

  def apply() =
    pomExtra := <url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git@github.com:hmrc/releaser.git</connection>
        <developerConnection>scm:git@github.com:hmrc/releaser.git</developerConnection>
        <url>git@github.com:hmrc/releaser.git</url>
      </scm>
      <developers>
        <developer>
          <id>duncancrawford</id>
          <name>Duncan Crawford</name>
          <url>http://www.equalexperts.com</url>
        </developer>
        <developer>
          <id>charleskubicek</id>
          <name>Charles Kubicek</name>
          <url>http://www.equalexperts.com</url>
        </developer>
        <developer>
          <id>stevesmith</id>
          <name>Steve Smith</name>
          <url>http://www.equalexperts.com</url>
        </developer>
      </developers>
}
