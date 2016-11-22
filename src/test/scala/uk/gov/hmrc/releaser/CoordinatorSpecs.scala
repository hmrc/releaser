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

package uk.gov.hmrc.releaser

import java.nio.file.Path
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.joda.time.DateTime
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.releaser.github.{CommitSha, Repo}

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

class CoordinatorSpecs extends WordSpec with Matchers with OptionValues with TryValues {

  import Builders._

  "the coordinator" should {

    "release version 1.3.1 of a library with an assembly, not modifying the assembly manifest, when given the inputs 'lib', '1.3.0-1-g21312cc' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = new DummyBintrayRepoConnector(
        jarResoure = Some("/lib/lib_2.11-1.3.0-1-g21312cc.jar"),
        bintrayFiles = Set("/lib/lib_2.11-1.3.0-1-g21312cc.pom", "/lib/lib_2.11-1.3.0-1-g21312cc-assembly.jar"))

      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("lib", Repo("lib"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseType.HOTFIX) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 3
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((assemblyVersion, assemblyFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("-assembly.jar"))
      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("1.3.1.jar"))

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "1.3.1"
      jarVersion.version.value shouldBe "1.3.1"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "1.3.1"
    }

    "release version 2.0.0 of a maven-based service when given the inputs 'help-frontend', '1.26.0-3-gd7ed03c' and 'major' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = new DummyBintrayRepoConnector(
        jarResoure = Some("/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.jar"),
        bintrayFiles = Set(
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.pom",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc.md5",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c-sources.jar"))

      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "help-frontend", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("help-frontend", Repo("help-frontend"), ReleaseCandidateVersion("1.26.0-3-gd7ed03c"), ReleaseType.MAJOR) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 4
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("2.0.0.jar"))

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "2.0.0"
      jarVersion.version.value shouldBe "2.0.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "2.0.0"
    }

    "release version 1.4.0 of a maven-based library when given the inputs 'time', '1.3.0-1-g21312cc' and 'minor' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = new DummyBintrayRepoConnector()
      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("time", Repo("time"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseType.MINOR) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 2
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((pomVersion, pomFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("1.4.0.jar"))

      jarFile.getFileName.toString should endWith(".jar")
      jarVersion.version.value shouldBe "1.4.0"

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "1.4.0"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "1.4.0"
    }

//    "Require only a .pom and a commit manifest in order to release an artifact" in {
//
//      val fakeRepoConnector = Builders.buildConnector(
//        "",
//        None,
//        Set("/lib/commit.mf", "/lib/lib_2.11-1.3.0-1-g21312cc.pom")
//      )
//
//      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector
//
//      val releaser = buildDefaultReleaser(
//        repositoryFinder = successfulRepoFinder(mavenRepository),
//        connectorBuilder = fakeRepoConnectorBuilder,
//        artefactMetaData = ArtefactMetaData("sha", "lib", DateTime.now()))
//
//      releaser.start("lib", Repo("lib"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseVersion("0.9.9")) match {
//        case Failure(e) => fail(e)
//        case _ =>
//      }
//
//      fakeRepoConnector.uploadedFiles.size shouldBe 2
//
//      val Some((_, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
//
//      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor
//      publishedDescriptor should not be None
//
//      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
//      pomVersionText shouldBe "0.9.9"
//    }

    "fail when given the sha in the pom does not exist" in {
      val expectedException = new scala.Exception("no commit message")

      val taggerAndReleaser = new DummyTagAndRelease {
        override def verifyGithubTagExists(repo: Repo, sha: CommitSha): Try[Unit] = Failure(expectedException)
      }

      val fakeRepoConnector = new DummyBintrayRepoConnector()
      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        taggerAndReleaser,
        fakeRepoConnector)

      coordinator.start("a", Repo("a"), aReleaseCandidateVersion, ReleaseType.MINOR) match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
      }
    }

    "fail when the artefact has already been released" in {

      val fakeRepoConnector = new DummyBintrayRepoConnector(targetExists = true)
      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("a", Repo("a"), aReleaseCandidateVersion, ReleaseType.MINOR) match {
        case Failure(e) => e shouldBe an [IllegalArgumentException]
        case Success(s) => fail(s"Should have failed with an IllegalArgumentException")
      }
    }

    "fail when the repository of an artefact isn't found" in {
      val fakeRepoConnector = new DummyBintrayRepoConnector(filesuffix = "/sbt-bobby/", Some("sbt-bobby.jar"), Set("ivy.xml")) {
        override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = Failure(new RuntimeException)
      }

      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("a", Repo("a"), aReleaseCandidateVersion, ReleaseType.MINOR) match {
        case Failure(e) => e.getMessage shouldBe "Didn't find a release candidate repository for 'a' in repos List(release-candidates, sbt-plugin-release-candidates)"
        case Success(s) => fail(s"Should have failed")
      }
    }

    "release version 0.8.2 of an ivy-based SBT plugin when given the inputs 'sbt-bobby', '0.8.1-4-ge733d26' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = new DummyBintrayRepoConnector(filesuffix = "/sbt-bobby/", Some("sbt-bobby.jar"), Set("ivy.xml")) {
        override def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
          if (repoName == "sbt-plugin-release-candidates") Success(Unit)
          else Failure(new RuntimeException)
        }
      }

      val coordinator = new Coordinator(
        tempDir(),
        (x) => Success(ArtefactMetaData("sha", "author", DateTime.now())),
        new DummyTagAndRelease,
        fakeRepoConnector)

      coordinator.start("sbt-bobby", Repo("sbt-bobby"), ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseType.HOTFIX) match {
          case Failure(e) => fail(e)
          case _ =>
        }

      fakeRepoConnector.uploadedFiles.size shouldBe 2
      fakeRepoConnector.lastPublishDescriptor should not be None

      val Some((_, ivyFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("ivy.xml"))
      val Some((jarVersion, jarFile, _)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("sbt-bobby.jar"))

      jarFile.getFileName.toString should be("sbt-bobby.jar")
      ivyFile.getFileName.toString should be("ivy.xml")

      val jarManifest = manifestFromZipFile(jarFile)
      jarManifest.value.getValue("Implementation-Version") shouldBe "0.8.2"
      jarVersion.version.value shouldBe "0.8.2"

      val ivyVersionText = (XML.loadFile(ivyFile.toFile) \ "info" \ "@revision").text
      ivyVersionText shouldBe "0.8.2"
    }
  }

  // TODO: This should be tested at the public interface
  //  "buildTargetFileName" should {
  //    "buildTargetFileName" in {
  //      val coord = buildDefaultCoordinator()
  //      val remotePath = "/time/time_2.11-1.3.0-1-g21312cc.jar"
  //      val versionMapping: VersionMapping = mavenVersionMapping(artefactName = "time", releaseVersion = "1.0.0")
  //      val targetFileName = coord.buildTargetFileName(versionMapping, remotePath, "time_2.11-1.3.0-1-g21312cc")
  //
  //      targetFileName shouldBe "time_2.11-1.0.0.jar"
  //    }
  //  }

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
