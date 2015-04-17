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

import org.scalatest.{Matchers, WordSpec}

class BintrayIvyPathsSpecs extends WordSpec with Matchers{

  val scalaVersion: String = "2.10"

  "BintrayIvyPathsSpecs" should {

    "Generate correct ivy urls" in {
      val fileUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar"
      val file = "sbt-bobby.jar"

      val repoName = "sbt-plugin-release-candidates"
      val artefactName = "sbt-bobby"
      val releaseCandidateVersion: String = "0.8.1-4-ge733d26"

      val ivyPaths = new BintrayIvyPaths("https://bintray.com/artifact/download/hmrc/")

      ivyPaths.jarFilenameFor(artefactName) shouldBe file
      ivyPaths.jarUrlFor(repoName, artefactName, scalaVersion, releaseCandidateVersion) shouldBe fileUrl
    }
  }
}
