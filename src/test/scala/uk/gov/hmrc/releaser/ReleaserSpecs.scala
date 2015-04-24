/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.releaser

import java.nio.file.{Paths, Files}

import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.releaser.MavenRepo

import scala.util.{Success, Try}


class ReleaserSpecs extends WordSpec with Matchers with OptionValues with MockitoSugar {

  "the releaser" should {

    "release version 0.1.1 when given the inputs 'sbt-bobby', '0.8.1-4-ge733d26' and 'patch' as the artefact, release candidate and release type" in {
      val tempDir = Files.createTempDirectory("tmp")
      val paths = new BintrayMavenPaths()

      val fakeRepositories = new Repositories {
        override def findReposOfArtefact(artefactName: String): Try[RepoFlavour] = {
          Success(new BintrayRepository("sbt-plugin-release-candidates", "sbt-plugin-releases") with IvyRepo)
        }

        override def connector: BintrayMetaConnector = ???
        override def repos: Seq[RepoFlavour] = ???
      }

      def connectorBuilder(p: PathBuilder): RepoConnector = Builders.buildConnector(
        "/sbt-bobby/sbt-bobby.jar",
        "/sbt-bobby/ivy.xml"
      )

      var calledSourceVersion: VersionDescriptor = null
      var calledTargetVersion: VersionDescriptor = null

      val coordinatorBuilder = (r: RepoConnector, b: PathBuilder, t:Transformer) => {
        new Coordinator(tempDir, r, b, new PomTransformer(Paths.get("/tmp"))) {
          override def start(sourceVersion: VersionDescriptor, targetVersion: VersionDescriptor): Try[Unit] = {
            calledSourceVersion = sourceVersion
            calledTargetVersion = targetVersion
            Success()
          }
        }
      }

      new Releaser(tempDir, fakeRepositories, connectorBuilder, coordinatorBuilder)
        .publishNewVersion("sbt-bobby", "0.8.1-4-ge733d26", "0.1.1")

      calledSourceVersion shouldBe VersionDescriptor(repo = "sbt-plugin-release-candidates", "sbt-bobby", "2.10", "0.8.1-4-ge733d26")
      calledTargetVersion shouldBe VersionDescriptor(repo = "sbt-plugin-releases", "sbt-bobby", "2.10", "0.1.1")

    }

    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {
      val tempDir = Files.createTempDirectory("tmp")
      val paths = new BintrayMavenPaths()

      val fakeRepositories = new Repositories {
        override def findReposOfArtefact(artefactName: String): Try[RepoFlavour] = {
          Success(new BintrayRepository("release-candidates", "releases") with MavenRepo)
        }

        override def connector: BintrayMetaConnector = ???
        override def repos: Seq[RepoFlavour] = ???
      }

      def connectorBuilder(p: PathBuilder): RepoConnector = Builders.buildConnector(
        "/time/time_2.11-1.3.0-1-g21312cc.jar",
        "/time/time_2.11-1.3.0-1-g21312cc.pom"
      )

      var calledSourceVersion: VersionDescriptor = null
      var calledTargetVersion: VersionDescriptor = null

      val coordinatorBuilder = (r: RepoConnector, b: PathBuilder, t:Transformer) => {
        new Coordinator(tempDir, r, b, t) {
          override def start(sourceVersion: VersionDescriptor, targetVersion: VersionDescriptor): Try[Unit] = {
            calledSourceVersion = sourceVersion
            calledTargetVersion = targetVersion
            Success()
          }
        }
      }

      new Releaser(tempDir, fakeRepositories, connectorBuilder, coordinatorBuilder)
        .publishNewVersion("time", "1.3.0-1-g21312cc", "0.9.9")

      calledSourceVersion shouldBe VersionDescriptor(repo = "release-candidates", "time", "2.11", "1.3.0-1-g21312cc")
      calledTargetVersion shouldBe VersionDescriptor(repo = "releases", "time", "2.11", "0.9.9")

    }
  }
}
