/*
 * Copyright 2019 HM Revenue & Customs
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

class BintrayPathsSpecs extends WordSpec with Matchers {

  "BintrayPaths" should {

    val repoName = "sbt-plugin-release-candidates"
    val artefactName = "sbt-bobby"
    val githubRepoName = "sbt-bobby"

    "Generate correct URL for publishing a package in Bintray" in {
      val expectedUrl = s"https://bintray.com/api/v1/content/hmrc/releases/$artefactName/0.9.9/publish"

      val repoName = "releases"
      val version = VersionDescriptor(repoName, artefactName, githubRepoName, "0.9.9")

      BintrayPaths.publishUrlFor(version) shouldBe expectedUrl
    }
  }

}
