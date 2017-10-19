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

package uk.gov.hmrc.releaser

import java.nio.file.{Files, Path}
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.mockito.Mockito.when
import org.mockito.Matchers.any
import org.joda.time.DateTime
import org.joda.time.DateTimeZone._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.Logger
import uk.gov.hmrc.releaser.bintray.FakeBintrayRepoConnector
import uk.gov.hmrc.releaser.github.{CommitSha, FakeGithubTagAndRelease, Repo}

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

class CoordinatorSpecs extends WordSpec with Matchers with OptionValues with TryValues with MockitoSugar with Logger {

  private def tempDir() = Files.createTempDirectory("tmp")

  val aReleaseCandidateVersion = ReleaseCandidateVersion("1.3.0-1-g21312cc")
  val aReleaseVersion = ReleaseVersion("1.3.1")
  val anArtefactMetaData = new ArtefactMetaData("803749", "ck", DateTime.now())

  "the coordinator" should {

    "release version 1.3.1 of a library with an assembly, not modifying the assembly manifest, when given the inputs 'libr', '1.3.0-1-g21312cc' and 'hotfix' as the artefact, release candidate and release type" in {

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk/gov/hmrc/libr_2.11/1.3.0-1-g21312cc"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/libr/",
        jarResource = Some(s"$root/libr_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set(s"$root/libr_2.11-1.3.0-1-g21312cc.pom", s"$root/libr_2.11-1.3.0-1-g21312cc-assembly.jar"))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("libr", Repo("libr"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseType.HOTFIX, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeRepoConnector.lastPublishDescriptor should not be None

      fakeRepoConnector.downloadedFiles.size shouldBe 3
      fakeRepoConnector.downloadedFiles should contain allOf(
        "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/libr_2.11/1.3.0-1-g21312cc/libr_2.11-1.3.0-1-g21312cc.pom",
        "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/libr_2.11/1.3.0-1-g21312cc/libr_2.11-1.3.0-1-g21312cc-assembly.jar",
        "https://bintray.com/artifact/download/hmrc/release-candidates/uk/gov/hmrc/libr_2.11/1.3.0-1-g21312cc/libr_2.11-1.3.0-1-g21312cc.jar")

      fakeRepoConnector.uploadedFiles.size shouldBe 3
      fakeRepoConnector.uploadedFiles.map { case (_,_,url) => url } should contain allOf(
        "https://bintray.com/api/v1/maven/hmrc/releases/libr/uk/gov/hmrc/libr_2.11/1.3.1/libr_2.11-1.3.1.pom",
        "https://bintray.com/api/v1/maven/hmrc/releases/libr/uk/gov/hmrc/libr_2.11/1.3.1/libr_2.11-1.3.1-assembly.jar",
        "https://bintray.com/api/v1/maven/hmrc/releases/libr/uk/gov/hmrc/libr_2.11/1.3.1/libr_2.11-1.3.1.jar")

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("1.3.1.jar"))

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "1.3.1"
      jarVersion.version shouldBe "1.3.1"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "1.3.1"
    }

    "release version 2.0.0 of a maven-based service when given the inputs 'help-frontend', '1.26.0-3-gd7ed03c' and 'major' as the artefact, release candidate and release type" in {

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk/gov/hmrc/help-frontend_2.11/1.26.0-3-gd7ed03c"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/help-frontend/",
        jarResource = Some(s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c.jar"),
        bintrayFiles = Set(
          s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c.pom",
          s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz",
          s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc",
          s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc.md5",
          s"$root/help-frontend_2.11-1.26.0-3-gd7ed03c-sources.jar"))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("help-frontend", Repo("help-frontend"), ReleaseCandidateVersion("1.26.0-3-gd7ed03c"), ReleaseType.MAJOR, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 4
      fakeRepoConnector.lastPublishDescriptor should not be None

      fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("-sources.jar")) should not be None
      fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".tgz.asc")) shouldBe None
      fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".tgz.asc.md5")) shouldBe None

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("2.0.0.jar"))

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "2.0.0"
      jarVersion.version shouldBe "2.0.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "2.0.0"
    }

    "release version 1.4.0 of a maven-based library when given the inputs 'time', '1.3.0-1-g21312cc' and 'minor' as the artefact, release candidate and release type" in {

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/time/",
        jarResource = Some(s"$root/time_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set(s"$root/time_2.11-1.3.0-1-g21312cc.pom"))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("time", Repo("time"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseType.MINOR, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 2
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("1.4.0.jar"))

      jarFile.getFileName.toString should endWith(".jar")
      jarVersion.version shouldBe "1.4.0"

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "1.4.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "1.4.0"
    }

    "release version 1.4.0 of a maven-based library with multiple Scala versions when given the inputs 'time', '1.3.0-1-g21312cc' and 'minor' as the artefact, release candidate and release type" in {

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root_2_11 = "uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc"
      val root_2_12 = "uk/gov/hmrc/time_2.12/1.3.0-1-g21312cc"

      val fakeBintrayRepoConnector = new FakeBintrayRepoConnector(
        "/time/",
        jarResource = Some(s"$root_2_11/time_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set(
          s"$root_2_11/time_2.11-1.3.0-1-g21312cc.pom",
          s"$root_2_12/time_2.12-1.3.0-1-g21312cc.jar",
          s"$root_2_12/time_2.12-1.3.0-1-g21312cc.pom"
        ))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeBintrayRepoConnector)
      coordinator.start("time", Repo("time"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseType.MINOR, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeBintrayRepoConnector.uploadedFiles.size shouldBe 4
      fakeBintrayRepoConnector.lastPublishDescriptor should not be None

      val Some((pomVersion, pomFile, _)) = fakeBintrayRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeBintrayRepoConnector.uploadedFiles.find(_._2.toString.endsWith("1.4.0.jar"))

      jarFile.getFileName.toString should endWith(".jar")
      jarVersion.version shouldBe "1.4.0"

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "1.4.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "1.4.0"
    }


    "Require only a .pom and a commit manifest in order to release an artifact" in {

      val sha = "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c"
      val author = "charleskubicek"
      val commitDate = new DateTime(2011, 6, 17, 14, 53, 35, UTC)

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromCommitManifest(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk/gov/hmrc/paye-estimator_sjs0.6_2.11/0.1.0-1-g1906708"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/paye-estimator/",
        jarResource = None,
        bintrayFiles = Set(
          s"$root/commit_sjs0.6_2.11-0.1.0-1-g1906708.mf",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708.pom",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708.jar",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708-javadoc.jar"))
 
      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("paye-estimator", Repo("paye-estimator"), ReleaseCandidateVersion("0.1.0-1-g1906708"), ReleaseType.MINOR, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeRepoConnector.lastPublishDescriptor should not be None

      fakeRepoConnector.uploadedFiles.size shouldBe 2
      fakeRepoConnector.uploadedFiles.map { case (_,_,url) => url } should contain allOf(
        "https://bintray.com/api/v1/maven/hmrc/releases/paye-estimator/uk/gov/hmrc/paye-estimator_sjs0.6_2.11/0.2.0/paye-estimator_sjs0.6_2.11-0.2.0.pom",
        "https://bintray.com/api/v1/maven/hmrc/releases/paye-estimator/uk/gov/hmrc/paye-estimator_sjs0.6_2.11/0.2.0/paye-estimator_sjs0.6_2.11-0.2.0.jar")

      fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("-javadoc.jar")) shouldBe None

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((sjsJarVersion, sjsJarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("paye-estimator_sjs0.6_2.11-0.2.0.jar"))

      sjsJarVersion.version shouldBe "0.2.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.2.0"
    }

    "Should rename and release any zip and tgz files that are present in the artefact but do not match the standard naming convention" in {
      val sha = "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c"
      val author = "charleskubicek"
      val commitDate = new DateTime(2011, 6, 17, 14, 53, 35, UTC)

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromCommitManifest(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk/gov/hmrc/paye-estimator_sjs0.6_2.11/0.1.0-1-g1906708"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/paye-estimator/",
        jarResource = None,
        bintrayFiles = Set(
          s"$root/commit_sjs0.6_2.11-0.1.0-1-g1906708.mf",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708.pom",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708.tgz",
          s"$root/paye-estimator_sjs0.6_2.11-0.1.0-1-g1906708.zip"))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("paye-estimator", Repo("paye-estimator"), ReleaseCandidateVersion("0.1.0-1-g1906708"), ReleaseType.MINOR, "some release notes") match {
        case Failure(e) =>
          log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
          fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 3
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((tgzVersion, _, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("paye-estimator_sjs0.6_2.11-0.2.0.tgz"))
      val Some((zipVersion, _, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("paye-estimator_sjs0.6_2.11-0.2.0.zip"))

      tgzVersion.version shouldBe "0.2.0"
      zipVersion.version shouldBe "0.2.0"
    }

    "fail when given the sha in the jar manifest does not exist" in {
      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val expectedException = new scala.Exception("no commit message")
      val taggerAndReleaser = new FakeGithubTagAndRelease {
        override def verifyGithubTagExists(repo: Repo, sha: CommitSha): Try[Unit] = Failure(expectedException)
      }

      val root = "uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/time/",
        jarResource = Some(s"$root/time_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set(s"$root/time_2.11-1.3.0-1-g21312cc.pom"))

      val coordinator = new Coordinator(tempDir(), metaDataProvider, taggerAndReleaser, fakeRepoConnector)
      coordinator.start("time", Repo("time"), aReleaseCandidateVersion, ReleaseType.MINOR, "some release notes") match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
      }
    }

    "fail when the artefact has already been released" in {

      val root = "uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        "/time/",
        jarResource = Some(s"$root/time_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set(s"$root/time_2.11-1.3.0-1-g21312cc.pom"), targetExists = true)

      val coordinator = new Coordinator(tempDir(), mock[MetaDataProvider], new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("time", Repo("time"), aReleaseCandidateVersion, ReleaseType.MINOR, "some release notes") match {
        case Failure(e) => e shouldBe an [IllegalArgumentException]
        case Success(s) => fail(s"Should have failed with an IllegalArgumentException")
      }
    }

    "fail when the repository of an artefact isn't found" in {

      val fakeRepoConnector = new FakeBintrayRepoConnector(filesuffix = "/sbt-bobby/", Some("sbt-bobby.jar"), Set("ivy.xml")) {
        override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = Failure(new RuntimeException)
      }

      val coordinator = new Coordinator(tempDir(), mock[MetaDataProvider], new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("a", Repo("a"), aReleaseCandidateVersion, ReleaseType.MINOR, "some release notes") match {
        case Failure(e) => e.getMessage shouldBe "Didn't find a release candidate repository for 'a' in repos List(release-candidates, sbt-plugin-release-candidates)"
        case Success(s) => fail(s"Should have failed")
      }
    }

    "release version 0.8.2 of an ivy-based SBT plugin when given the inputs 'sbt-bobby', '0.8.1-4-ge733d26' and 'hotfix' as the artefact, release candidate and release type" in {

      val metaDataProvider = mock[MetaDataProvider]
      when(metaDataProvider.fromJarFile(any())).thenReturn(Success(ArtefactMetaData("sha", "author", DateTime.now())))

      val root = "uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26"
      val fakeRepoConnector = new FakeBintrayRepoConnector(
        filesuffix = "/sbt-bobby/",
        Some(s"$root/jars/sbt-bobby.jar"),
        Set(s"$root/ivys/ivy.xml", s"$root/ivys/ivy.xml.md5"))
      {
        override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
          if (repoName == "sbt-plugin-release-candidates") Success(Unit)
          else Failure(new RuntimeException)
        }
      }

      val coordinator = new Coordinator(tempDir(), metaDataProvider, new FakeGithubTagAndRelease, fakeRepoConnector)
      coordinator.start("sbt-bobby", Repo("sbt-bobby"), ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseType.HOTFIX, "some release notes") match {
          case Failure(e) =>
            log.error(s"Test failed with: ${e.getMessage} - ${e.toString}")
            fail(e)
          case _ =>
        }

      fakeRepoConnector.lastPublishDescriptor should not be None

      fakeRepoConnector.downloadedFiles.size shouldBe 2
      fakeRepoConnector.downloadedFiles should contain allOf(
        "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/ivys/ivy.xml",
        "https://bintray.com/artifact/download/hmrc/sbt-plugin-release-candidates/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.1-4-ge733d26/jars/sbt-bobby.jar")

      fakeRepoConnector.uploadedFiles.size shouldBe 2
      fakeRepoConnector.uploadedFiles.map { case (_,_,url) => url } should contain allOf(
        "https://bintray.com/api/v1/content/hmrc/sbt-plugin-releases/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.2/ivys/ivy.xml",
        "https://bintray.com/api/v1/content/hmrc/sbt-plugin-releases/uk.gov.hmrc/sbt-bobby/scala_2.10/sbt_0.13/0.8.2/jars/sbt-bobby.jar")

      val Some((_, ivyFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("ivy.xml"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("sbt-bobby.jar"))

      jarFile.getFileName.toString should be("sbt-bobby.jar")
      ivyFile.getFileName.toString should be("ivy.xml")

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "0.8.2"
      jarVersion.version shouldBe "0.8.2"

      val ivyVersionText = (XML.loadFile(ivyFile.toFile) \ "info" \ "@revision").text
      ivyVersionText shouldBe "0.8.2"
    }
  }

  private def manifestFromZipFile(file: Path): Option[Attributes] = {
    val zipFile: ZipFile = new ZipFile(file.toFile)

    zipFile.entries().toList.find { ze =>
      ze.getName == "META-INF/MANIFEST.MF"
    }.flatMap { ze =>
      Try(new jar.Manifest(zipFile.getInputStream(ze))).map { man =>
        man.getMainAttributes
      }.toOption
    }
  }
}
