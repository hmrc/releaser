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
import uk.gov.hmrc.releaser.{ArtifactType, ArtifactClassifier}

class BintrayIvyPathsSpecs extends WordSpec with Matchers{

  val jarClassifier = ArtifactClassifier(ArtifactType.JAR, "jar", None, true, true)
  val pomClassifier = ArtifactClassifier(ArtifactType.POM, "pom", None, true)
  val srcClassifier = ArtifactClassifier(ArtifactType.SOURCE_JAR, "jar", Some("-sources"), false)
  val docClassifier = ArtifactClassifier(ArtifactType.DOC_JAR, "jar", Some("-javadoc"), false)

  "BintrayIvyPathsSpecs" should {

    val ivyPaths = new BintrayIvyPaths(){
      override def scalaVersion: String = "2.10"
    }

    val repoName = "sbt-plugin-release-candidates"
    val artefactName = "sbt-bobby"
    val repo = Repo("sbt-bobby")

    "Generate URL for files on Bintray" in {
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/ivys/ivy.xml"
      val expectedDocUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/docs/sbt-bobby-javadoc.jar"
      val expectedSrcUrl = "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/srcs/sbt-bobby-sources.jar"

      val jarFile = "sbt-bobby.jar"
      val pomFile = "ivy.xml"
      val docFile = "sbt-bobby-javadoc.jar"
      val srcFile = "sbt-bobby-sources.jar"

      val releaseCandidateVersion = ReleaseCandidateVersion("0.8.1-4-ge733d26")

      val jarVersion = VersionDescriptor(repoName, artefactName, jarClassifier, repo, releaseCandidateVersion)
      val pomVersion = VersionDescriptor(repoName, artefactName, pomClassifier, repo, releaseCandidateVersion)
      val srcVersion = VersionDescriptor(repoName, artefactName, srcClassifier, repo, releaseCandidateVersion)
      val docVersion = VersionDescriptor(repoName, artefactName, docClassifier, repo, releaseCandidateVersion)

      ivyPaths.artifactFilenameFor(jarVersion) shouldBe jarFile
      ivyPaths.artifactFilenameFor(pomVersion) shouldBe pomFile
      ivyPaths.artifactFilenameFor(srcVersion) shouldBe srcFile
      ivyPaths.artifactFilenameFor(docVersion) shouldBe docFile

      ivyPaths.artifactDownloadFor(jarVersion) shouldBe expectedJarUrl
      ivyPaths.artifactDownloadFor(pomVersion) shouldBe expectedPomUrl
      ivyPaths.artifactDownloadFor(srcVersion) shouldBe expectedSrcUrl
      ivyPaths.artifactDownloadFor(docVersion) shouldBe expectedDocUrl
    }

    "Generate correct URL for uploading a jar file to Bintray" in {
      val expectedJarUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/jars/sbt-bobby.jar"
      val expectedSrcUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/srcs/sbt-bobby-sources.jar"
      val expectedDocUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/docs/sbt-bobby-javadoc.jar"
      val expectedIvyUrl = "https://bintray.com/api/v1/content/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.9.0/ivys/ivy.xml"

      val jarVersion = VersionDescriptor(repoName, artefactName, jarClassifier, repo, ReleaseVersion("0.9.0"))
      val pomVersion = VersionDescriptor(repoName, artefactName, pomClassifier, repo, ReleaseVersion("0.9.0"))
      val srcVersion = VersionDescriptor(repoName, artefactName, srcClassifier, repo, ReleaseVersion("0.9.0"))
      val docVersion = VersionDescriptor(repoName, artefactName, docClassifier, repo, ReleaseVersion("0.9.0"))

      ivyPaths.artifactUploadFor(jarVersion) shouldBe expectedJarUrl
      ivyPaths.artifactUploadFor(pomVersion) shouldBe expectedIvyUrl
      ivyPaths.artifactUploadFor(srcVersion) shouldBe expectedSrcUrl
      ivyPaths.artifactUploadFor(docVersion) shouldBe expectedDocUrl

    }
  }
}
