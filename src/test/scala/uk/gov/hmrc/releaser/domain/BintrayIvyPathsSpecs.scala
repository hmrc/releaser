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

package uk.gov.hmrc.releaser.domain

import org.scalatest.{Matchers, WordSpec}

class BintrayIvyPathsSpecs extends WordSpec with Matchers{

  "BintrayIvyPathsSpecs" should {

    val ivyPaths = new BintrayIvyPaths(){
      override def scalaVersion: String = "2.10"
    }

    "Generate URL for files on Bintray" in {
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/ivys/ivy.xml"

      val jarFile = "sbt-bobby.jar"
      val pomFile = "ivy.xml"

      val repoName = "sbt-plugin-release-candidates"
      val artefactName = "sbt-bobby"
      val releaseCandidateVersion = "0.8.1-4-ge733d26"

      val version = VersionDescriptor(repoName, artefactName, releaseCandidateVersion)

      ivyPaths.jarFilenameFor(version) shouldBe jarFile
      ivyPaths.pomFilenameFor(version) shouldBe pomFile

      ivyPaths.jarDownloadFor(version) shouldBe expectedJarUrl
      ivyPaths.pomDownloadUrlFor(version) shouldBe expectedPomUrl
    }

    "Generate correct URL for uploading a jar file to Bintray" in {
      val expectedJarUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/jars/sbt-bobby.jar"
      val expectedIvyUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/ivys/ivy.xml"

      val repoName = "sbt-plugin-release-candidates"
      val artefactName = "sbt-bobby"
      val versionString = "0.9.0"

      val version = VersionDescriptor(repoName, artefactName, versionString)

      ivyPaths.jarUploadFor(version) shouldBe expectedJarUrl
      ivyPaths.pomUploadFor(version) shouldBe expectedIvyUrl

    }
  }
}
