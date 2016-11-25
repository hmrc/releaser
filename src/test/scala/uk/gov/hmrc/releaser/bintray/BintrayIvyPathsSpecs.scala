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

package uk.gov.hmrc.releaser.bintray

import org.scalatest.{Matchers, WordSpec}

class BintrayIvyPathsSpecs extends WordSpec with Matchers{

  "BintrayIvyPathsSpecs" should {

    val ivyPaths = new BintrayIvyPaths(){
      override def scalaVersion: String = "2.10"
    }

    val repoName = "sbt-plugin-release-candidates"
    val artefactName = "sbt-bobby"
    val githubRepoName = "sbt-bobby"

    "Generate URL for files on Bintray" in {
      val expectedAssemblyJarUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby-assembly.jar"
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/ivys/ivy.xml"

      val releaseCandidateVersion = "0.8.1-4-ge733d26"

      val version = VersionDescriptor(repoName, artefactName, githubRepoName, releaseCandidateVersion)

      ivyPaths.jarFilenameFor(version) shouldBe "sbt-bobby.jar"
      ivyPaths.fileDownloadFor(version, "sbt-bobby-assembly.jar") shouldBe expectedAssemblyJarUrl

      ivyPaths.jarDownloadFor(version) shouldBe expectedJarUrl
      ivyPaths.fileDownloadFor(version, "ivy.xml") shouldBe expectedPomUrl
    }

    "Generate correct URL for uploading files to Bintray" in {
      val expectedJarUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/jars/sbt-bobby.jar"
      val expectedIvyUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/ivys/ivy.xml"
      val expectedSrcUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/srcs/sbt-bobby-sources.jar"
      val expectedDocUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/docs/sbt-bobby-javadoc.jar"

      val version = VersionDescriptor(repoName, artefactName, githubRepoName, "0.9.0")

      ivyPaths.jarUploadFor(version) shouldBe expectedJarUrl
      ivyPaths.fileUploadFor(version, "ivy.xml") shouldBe expectedIvyUrl
      ivyPaths.fileUploadFor(version, "sbt-bobby-sources.jar") shouldBe expectedSrcUrl
      ivyPaths.fileUploadFor(version, "sbt-bobby-javadoc.jar") shouldBe expectedDocUrl

      intercept[IllegalArgumentException] {
       ivyPaths.fileUploadFor(version, "sbt-bobby.exe")
      }
    }
  }
}
