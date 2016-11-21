/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.domain

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.releaser.Repo
import uk.gov.hmrc.releaser.bintray.{BintrayMavenPaths, BintrayPaths}

class BintrayMavenPathsSpecs extends WordSpec with Matchers{

  "BintrayMavenPathsSpecs" should {

    val mavenPaths = new BintrayMavenPaths() {
      override def scalaVersion: String = "2.10"
    }

    val artefactName = "time"
    val repo = Repo("time")

    "Generate URL for a jar and pom file on Bintray" in {
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.pom"

      val repoName = "release-candidates"
      val artefactName = "time"
      val releaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")

      val version = VersionDescriptor(repoName, artefactName, repo, releaseCandidateVersion)

      mavenPaths.jarFilenameFor(version) shouldBe "time_2.10-1.3.0-1-g21312cc.jar"
      mavenPaths.jarDownloadFor(version) shouldBe expectedJarUrl

      mavenPaths.filenameFor(version, ".pom") shouldBe "time_2.10-1.3.0-1-g21312cc.pom"
      mavenPaths.fileDownloadFor(version, "time_2.10-1.3.0-1-g21312cc.pom") shouldBe expectedPomUrl
    }

    "Generate correct URL for uploading a jar file to Bintray" in {
      val expectedUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0.jar"

      val repoName = "releases"
      val artefactName = "time"

      val version = VersionDescriptor(repoName, artefactName, repo, ReleaseVersion("1.4.0"))

      mavenPaths.jarUploadFor(version) shouldBe expectedUrl

    }

    // TODO: this is not maven specific
    "Generate correct URL for publishing a package in Bintray" in {
      val expectedUrl = "https://bintray.com/api/v1/content/hmrc/releases/time/0.9.9/publish"

      val repoName = "releases"
      val version = VersionDescriptor(repoName, artefactName, repo, ReleaseVersion("0.9.9"))

      BintrayPaths.publishUrlFor(version) shouldBe expectedUrl

    }
  }
}
