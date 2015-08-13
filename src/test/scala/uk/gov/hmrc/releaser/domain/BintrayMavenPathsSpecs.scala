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
import uk.gov.hmrc.releaser.{ArtifactClassifier, ArtifactType}

class BintrayMavenPathsSpecs extends WordSpec with Matchers{

  val jarClassifier = ArtifactClassifier(ArtifactType.JAR, "jar", None, true)
  val pomClassifier = ArtifactClassifier(ArtifactType.POM, "pom", None)
  val srcClassifier = ArtifactClassifier(ArtifactType.SOURCE_JAR, "jar", Some("-sources"))
  val docClassifier = ArtifactClassifier(ArtifactType.DOC_JAR, "jar", Some("-javadoc"))
  val tgzClassifier = ArtifactClassifier(ArtifactType.TGZ, "tgz", None)

  "BintrayMavenPathsSpecs" should {

    val mavenPaths = new BintrayMavenPaths() {
      override def scalaVersion: String = "2.10"
    }

    val artefactName = "time"
    val repo = Repo("time")

    "Generate URL for a jar file on Bintray" in {
      val expectedJarUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.jar"
      val expectedPomUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.pom"
      val expectedSrcUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc-sources.jar"
      val expectedDocUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc-javadoc.jar"
      val expectedTgzUrl = "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/time_2.10/1.3.0-1-g21312cc/time_2.10-1.3.0-1-g21312cc.tgz"

      val repoName = "release-candidates"
      val artefactName = "time"
      val releaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")

      val jarVersion = VersionDescriptor(repoName, artefactName, jarClassifier, repo, releaseCandidateVersion)
      val pomVersion = VersionDescriptor(repoName, artefactName, pomClassifier, repo, releaseCandidateVersion)
      val srcVersion = VersionDescriptor(repoName, artefactName, srcClassifier, repo, releaseCandidateVersion)
      val docVersion = VersionDescriptor(repoName, artefactName, docClassifier, repo, releaseCandidateVersion)
      val tgzVersion = VersionDescriptor(repoName, artefactName, tgzClassifier, repo, releaseCandidateVersion)

      mavenPaths.artifactFilenameFor(jarVersion) shouldBe "time_2.10-1.3.0-1-g21312cc.jar"
      mavenPaths.artifactDownloadFor(jarVersion) shouldBe expectedJarUrl

      mavenPaths.artifactFilenameFor(pomVersion) shouldBe "time_2.10-1.3.0-1-g21312cc.pom"
      mavenPaths.artifactDownloadFor(pomVersion) shouldBe expectedPomUrl

      mavenPaths.artifactFilenameFor(srcVersion) shouldBe "time_2.10-1.3.0-1-g21312cc-sources.jar"
      mavenPaths.artifactDownloadFor(srcVersion) shouldBe expectedSrcUrl

      mavenPaths.artifactFilenameFor(docVersion) shouldBe "time_2.10-1.3.0-1-g21312cc-javadoc.jar"
      mavenPaths.artifactDownloadFor(docVersion) shouldBe expectedDocUrl

      mavenPaths.artifactFilenameFor(tgzVersion) shouldBe "time_2.10-1.3.0-1-g21312cc.tgz"
      mavenPaths.artifactDownloadFor(tgzVersion) shouldBe expectedTgzUrl

    }

    "Generate correct URL for uploading a jar file to Bintray" in {
      val expectedJarUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0.jar"
      val expectedPomUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0.pom"
      val expectedSrcUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0-sources.jar"
      val expectedDocUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0-javadoc.jar"
      val expectedTgzUrl = "https://bintray.com/api/v1/maven/hmrc/releases/time/uk/gov/hmrc/time_2.10/1.4.0/time_2.10-1.4.0.tgz"

      val repoName = "releases"
      val artefactName = "time"

      val jarVersion = VersionDescriptor(repoName, artefactName, jarClassifier, repo, ReleaseVersion("1.4.0"))
      val pomVersion = VersionDescriptor(repoName, artefactName, pomClassifier, repo, ReleaseVersion("1.4.0"))
      val srcVersion = VersionDescriptor(repoName, artefactName, srcClassifier, repo, ReleaseVersion("1.4.0"))
      val docVersion = VersionDescriptor(repoName, artefactName, docClassifier, repo, ReleaseVersion("1.4.0"))
      val tgzVersion = VersionDescriptor(repoName, artefactName, tgzClassifier, repo, ReleaseVersion("1.4.0"))

      mavenPaths.artifactUploadFor(jarVersion) shouldBe expectedJarUrl
      mavenPaths.artifactUploadFor(pomVersion) shouldBe expectedPomUrl
      mavenPaths.artifactUploadFor(srcVersion) shouldBe expectedSrcUrl
      mavenPaths.artifactUploadFor(docVersion) shouldBe expectedDocUrl
      mavenPaths.artifactUploadFor(tgzVersion) shouldBe expectedTgzUrl

    }

    "Generate correct URL for publishing a package in Bintray" in {
      val expectedUrl = "https://bintray.com/api/v1/content/hmrc/releases/time/0.9.9/publish"

      val repoName = "releases"
      val jarVersion = VersionDescriptor(repoName, artefactName, jarClassifier, repo, ReleaseVersion("0.9.9"))

      mavenPaths.publishUrlFor(jarVersion) shouldBe expectedUrl

    }
  }
}
