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

import java.nio.file.Files

import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.util.{Success, Try}

class ReleaserSpecs extends WordSpec with Matchers with OptionValues with MockitoSugar {

  "the releaser" should {

    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {
      val tempDir = Files.createTempDirectory("tmp")
      val paths = new BintrayMavenPaths()

      val fakeRepositories = new Repositories {
        override def findReposOfArtefact(artefactName: String): Try[RepositoryFlavour] = {
          Success(RepositoryFlavour(paths, "release-candidates", "releases"))
        }

        override def connector: BintrayMetaConnector = ???
        override def repos: Seq[RepositoryFlavour] = ???
      }

      def connectorBuilder(p: PathBuilder): RepoConnector = Builders.buildConnector()

      var calledSourceVersion: VersionDescriptor = null
      var calledTargetVersion: VersionDescriptor = null

      val coordinatorBuilder = (r: RepoConnector, b: PathBuilder) => {
        new Coordinator(tempDir, r, b) {
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
