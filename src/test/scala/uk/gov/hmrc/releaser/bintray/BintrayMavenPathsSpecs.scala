/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.bintray

import org.scalatest.{Matchers, WordSpec}

class BintrayMavenPathsSpecs extends WordSpec with Matchers{

  "BintrayMavenPathsSpecs" should {

    val mavenPaths = new BintrayMavenPaths() {
      override def scalaVersion: String = "2.10"
    }

    "Generate URL for a jar and pom file on Bintray" in {
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.pom"

      val repoName = "release-candidates"
      val artefactName = "time"
      val githubRepoName = "time"
      val releaseCandidateVersion = "1.3.0-1-g21312cc"

      val version = VersionDescriptor(repoName, artefactName, githubRepoName, releaseCandidateVersion)

      mavenPaths.jarFilenameFor(version) shouldBe "time_2.10-1.3.0-1-g21312cc.jar"
      mavenPaths.fileDownloadFor(version, "uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.pom") shouldBe expectedPomUrl
    }
  }
}
