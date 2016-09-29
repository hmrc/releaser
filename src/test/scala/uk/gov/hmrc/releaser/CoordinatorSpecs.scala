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

import java.nio.file.{Files, Path}
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.joda.time.DateTime
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.releaser.domain._
import uk.gov.hmrc.releaser.BintrayHttp

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

class CoordinatorSpecs extends WordSpec with Matchers with OptionValues with TryValues {

  import Builders._
  import RepoFlavours._

  "the coordinator" should {

    "release version 0.9.9 of a library with an assembly, not modifying the assembly manifest, when given the inputs 'time', '1.3.0-1-g21312cc' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector(
        "",
        "/lib/lib_2.11-1.3.0-1-g21312cc.jar",
        Set("/lib/lib_2.11-1.3.0-1-g21312cc.pom", "/lib/lib_2.11-1.3.0-1-g21312cc-assembly.jar")
      )

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(mavenRepository),
        connectorBuilder = fakeRepoConnectorBuilder,
        artefactMetaData = ArtefactMetaData("sha", "lib", DateTime.now()))

      releaser.start("lib", Repo("lib"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseVersion("0.9.9")) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 3

      val Some((assemblyVersion, assemblyFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("-assembly.jar"))
      val Some((pomVersion, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("9.jar"))

      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.9.9"

      val jarManifest = manifestFromZipFile(jarFile)
      val assemblyManifest = manifestFromZipFile(assemblyFile)

      jarManifest.value.getValue("Implementation-Version") shouldBe "0.9.9"

      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
    }

    "release version 2.0.0 of a maven-based service when given the inputs 'help-frontend', '1.26.0-3-gd7ed03c' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector(
        "",
        "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.jar",
        Set(
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.pom",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz.asc.md5",
          "/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c-sources.jar"
        ))

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(mavenRepository),
        connectorBuilder = fakeRepoConnectorBuilder,
        artefactMetaData = ArtefactMetaData("sha", "help-frontend", DateTime.now()))

      releaser.start("help-frontend", Repo("help-frontend"), ReleaseCandidateVersion("1.26.0-3-gd7ed03c"), ReleaseVersion("0.9.9")) match {
        case Failure(e) => fail(e)
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 4

      val Some((pomVersion, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("9.jar"))


      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.9.9"

      val manifest = manifestFromZipFile(jarFile)

      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
    }

    "extract the MetaData from the JSON grabbed from a repo" in {
      val mockedBintrayHttp = Mockito.mock(classOf[BintrayHttp])

      when(mockedBintrayHttp.get("https://bintray.com/api/v1/packages/hmrc/candidate-releases/time-to-pay-arrangement"))
        .thenReturn(Try("{\n\"name\": \"time-to-pay-arrangement\",\n\"repo\": \"release-candidates\",\n\"owner\": \"hmrc\",\n\"desc\": \"time-to-pay-arrangement release-candidates\",\n\"labels\": [],\n\"attribute_names\": [],\n\"licenses\": [\n\"Apache-2.0\"\n],\n\"custom_licenses\": [],\n\"followers_count\": 0,\n\"created\": \"2016-09-13T13:00:47.021Z\",\n\"website_url\": \"https://github.com/hmrc/time-to-pay-arrangement\",\n\"issue_tracker_url\": \"https://github.com/hmrc/time-to-pay-arrangement/issues\",\n\"linked_to_repos\": [],\n\"permissions\": [],\n\"versions\": [\n\"v0.1.0-70-gd81945a\",\n\"v0.1.0-63-g2c8c2e3\",\n\"v0.1.0-62-g20be0ad\",\n\"v0.1.0-61-g10ec4eb\",\n\"v0.1.0-60-g369734d\",\n\"v0.1.0-59-g26cd48d\",\n\"v0.1.0-58-ge34f2f4\",\n\"v0.1.0-57-g0dc418e\",\n\"v0.1.0-56-gd81363e\",\n\"v0.1.0-46-gaae5701\",\n\"v0.1.0-45-g89acdd5\",\n\"v0.1.0-44-g8eb5c82\",\n\"v0.1.0-43-g8292653\",\n\"v0.1.0-42-g8eb9e72\",\n\"v0.1.0-41-g6bd5be4\",\n\"v0.1.0-40-g80df6a0\",\n\"v0.1.0-39-gbb13ef8\",\n\"v0.1.0-38-g27a4f63\",\n\"v0.1.0-37-g2dba1f0\",\n\"v0.1.0-36-g433eb9a\",\n\"v0.1.0-35-gd0a9e3d\",\n\"v0.1.0-33-g3d301e0\",\n\"v0.1.0-32-g6b4b38e\",\n\"v0.1.0-31-g35c5e46\",\n\"v0.1.0-30-gf244161\",\n\"v0.1.0-29-g0a70573\",\n\"v0.1.0-28-ga895fe8\",\n\"v0.1.0-27-ge547b4b\",\n\"v0.1.0-26-g273d601\",\n\"v0.1.0-25-g7fd3e05\",\n\"v0.1.0-24-gca9ad66\",\n\"v0.1.0-22-gf9b2298\"\n],\n\"latest_version\": \"v0.1.0-70-gd81945a\",\n\"updated\": \"2016-09-27T09:40:12.779Z\",\n\"rating_count\": 0,\n\"system_ids\": [\n\"uk.gov.hmrc:time-to-pay-arrangement\"\n],\n\"vcs_url\": \"https://github.com/hmrc/time-to-pay-arrangement\",\n\"maturity\": \"\"\n}"))

      new BintrayMetaConnector(mockedBintrayHttp)
        .getRepoMetaData("candidate-releases", "time-to-pay-arrangement") match {
        case Success(r) => r.systemIDs shouldBe "time-to-pay-arrangement"
          r.repoName shouldBe "release-candidates"
          r.description shouldBe "time-to-pay-arrangement release-candidates"
          r.artefactName shouldBe "time-to-pay-arrangement"
      }
    }

    "check the system_id matches the artefact name when search through arefacts in a repository" in {
      val mockedBintrayHttp = Mockito.mock(classOf[BintrayHttp])

      when(mockedBintrayHttp.get("https://bintray.com/api/v1/packages/hmrc/candidate-releases/time-to-pay-arrangement"))
        .thenReturn(Try("{\n\"name\": \"time-to-pay-arrangement\",\n\"repo\": \"release-candidates\",\n\"owner\": \"hmrc\",\n\"desc\": \"time-to-pay-arrangement release-candidates\",\n\"labels\": [],\n\"attribute_names\": [],\n\"licenses\": [\n\"Apache-2.0\"\n],\n\"custom_licenses\": [],\n\"followers_count\": 0,\n\"created\": \"2016-09-13T13:00:47.021Z\",\n\"website_url\": \"https://github.com/hmrc/time-to-pay-arrangement\",\n\"issue_tracker_url\": \"https://github.com/hmrc/time-to-pay-arrangement/issues\",\n\"linked_to_repos\": [],\n\"permissions\": [],\n\"versions\": [\n\"v0.1.0-70-gd81945a\",\n\"v0.1.0-63-g2c8c2e3\",\n\"v0.1.0-62-g20be0ad\",\n\"v0.1.0-61-g10ec4eb\",\n\"v0.1.0-60-g369734d\",\n\"v0.1.0-59-g26cd48d\",\n\"v0.1.0-58-ge34f2f4\",\n\"v0.1.0-57-g0dc418e\",\n\"v0.1.0-56-gd81363e\",\n\"v0.1.0-46-gaae5701\",\n\"v0.1.0-45-g89acdd5\",\n\"v0.1.0-44-g8eb5c82\",\n\"v0.1.0-43-g8292653\",\n\"v0.1.0-42-g8eb9e72\",\n\"v0.1.0-41-g6bd5be4\",\n\"v0.1.0-40-g80df6a0\",\n\"v0.1.0-39-gbb13ef8\",\n\"v0.1.0-38-g27a4f63\",\n\"v0.1.0-37-g2dba1f0\",\n\"v0.1.0-36-g433eb9a\",\n\"v0.1.0-35-gd0a9e3d\",\n\"v0.1.0-33-g3d301e0\",\n\"v0.1.0-32-g6b4b38e\",\n\"v0.1.0-31-g35c5e46\",\n\"v0.1.0-30-gf244161\",\n\"v0.1.0-29-g0a70573\",\n\"v0.1.0-28-ga895fe8\",\n\"v0.1.0-27-ge547b4b\",\n\"v0.1.0-26-g273d601\",\n\"v0.1.0-25-g7fd3e05\",\n\"v0.1.0-24-gca9ad66\",\n\"v0.1.0-22-gf9b2298\"\n],\n\"latest_version\": \"v0.1.0-70-gd81945a\",\n\"updated\": \"2016-09-27T09:40:12.779Z\",\n\"rating_count\": 0,\n\"system_ids\": [\n\"uk.gov.hmrc:time-to-pay-arrangement\"\n],\n\"vcs_url\": \"https://github.com/hmrc/time-to-pay-arrangement\",\n\"maturity\": \"\"\n}"))

      val metaDataGetter = new BintrayMetaConnector(mockedBintrayHttp)
        .getRepoMetaData _

      val repoFinder = new Repositories(metaDataGetter)(Seq(mavenRepository, ivyRepository, gradleRepository)).findReposOfArtefact _
    }

    "release version 2.0.0 of a gradle-based service when given the inputs 'help-frontend', '1.26.0-3-gd7ed03c' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector(
        "",
        "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c.jar",
        Set(
          "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c.pom",
          "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c.tgz",
          "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c.tgz.asc",
          "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c.tgz.asc.md5",
          "/gradle-help-frontend/help-frontend-1.26.0-3-gd7ed03c-sources.jar"
        ))

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(gradleRepository),
        connectorBuilder = fakeRepoConnectorBuilder,
        artefactMetaData = ArtefactMetaData("sha", "gradle-help-frontend", DateTime.now()))

      releaser.start("help-frontend", Repo("help-frontend"), ReleaseCandidateVersion("1.26.0-3-gd7ed03c"), ReleaseVersion("0.9.9")) match {
        case Failure(e) => {
          println(e)
          fail(e)
        }
        case _ =>
      }

      fakeRepoConnector.uploadedFiles.size shouldBe 4

      val Some((pomVersion, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("9.jar"))


      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.9.9"

      val manifest = manifestFromZipFile(jarFile)

      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
    }

    "release version 0.9.9 of a maven-based library when given the inputs 'time', '1.3.0-1-g21312cc' and 'hotfix' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector(
        "",
        "/time/time_2.11-1.3.0-1-g21312cc.jar",
        Set("/time/time_2.11-1.3.0-1-g21312cc.pom"))

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(mavenRepository),
        connectorBuilder = fakeRepoConnectorBuilder,
        artefactMetaData = ArtefactMetaData("sha", "time", DateTime.now()))

      releaser.start("time", Repo("time"), ReleaseCandidateVersion("1.3.0-1-g21312cc"), ReleaseVersion("0.9.9")) match {
        case Failure(e) => fail(e)
        case _ =>
      }


      fakeRepoConnector.uploadedFiles.size shouldBe 2

      val Some((pomVersion, pomFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith(".pom"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("9.jar"))

      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      jarFile.getFileName.toString should endWith(".jar")
      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.9.9"

      val manifest = manifestFromZipFile(jarFile)

      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
    }

    "fail when given the sha in the pom does not exist" in {
      val expectedException = new scala.Exception("no commit message")

      val releaser = buildDefaultReleaser(
        githubRepoGetter = (a, b) => Failure(expectedException)
      )

      releaser.start("a", Repo("a"), aReleaseCandidateVersion, aReleaseVersion) match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
      }
    }

    "fail when the artefact has already been released" in {

      val fakeRepoConnector = Builders.buildConnector(
        "",
        "/time/time_2.11-1.3.0-1-g21312cc.jar",
        Set("/time/time_2.11-1.3.0-1-g21312cc.pom"), targetExists = true)

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(mavenRepository),
        connectorBuilder = fakeRepoConnectorBuilder)

      releaser.start("a", Repo("a"), aReleaseCandidateVersion, aReleaseVersion) match {
        case Failure(e) => e shouldBe an [IllegalArgumentException]
        case Success(s) => fail(s"Should have failed with an IllegalArgumentException")
      }
    }


    "fail when the repository of an artefact isn't found" in {
      val expectedException = new scala.Exception("repo fail")

      val releaser = buildDefaultReleaser(
        repositoryFinder = (a) => Failure(expectedException)
      )

      releaser.start("a", Repo("a"), aReleaseCandidateVersion, aReleaseVersion) match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
      }
    }

    "release version 0.1.1 of an ivy-based SBT plugin when given the inputs 'sbt-bobby', '0.8.1-4-ge733d26' and 'hotfix' as the artefact, release candidate and release type" in {

      val githubReleaseBuilder = new MockFunction2[ArtefactMetaData, VersionMapping]()
      val githubTagObjBuilder = new MockFunction3[Repo, ReleaseVersion, CommitSha, CommitSha]()
      val githubTagRefBuilder = new MockFunction3[Repo, ReleaseVersion, CommitSha, Unit]()

      val fakeRepoConnector = Builders.buildConnector(
        filesuffix = "/sbt-bobby/",
        "sbt-bobby.jar",
        Set("ivy.xml")
      )

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
          repositoryFinder = successfulRepoFinder(ivyRepository),
          connectorBuilder = fakeRepoConnectorBuilder,
          artefactMetaData = ArtefactMetaData("gitsha", "sbt-bobby", DateTime.now()),
          githubReleasePublisher = githubReleaseBuilder.build,
          githubTagObjPublisher = githubTagObjBuilder.build[CommitSha]("the-tag-sha"),
          githubTagRefPublisher = githubTagRefBuilder.build[Unit](Unit)
      )

        releaser.start("sbt-bobby", Repo("sbt-bobby"), ReleaseCandidateVersion("0.8.1-4-ge733d26"), ReleaseVersion("0.1.1")) match {
          case Failure(e) => fail(e)
          case _ =>
        }

      fakeRepoConnector.uploadedFiles.size shouldBe 2

      val Some((_, ivyFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("ivy.xml"))
      val Some((jarVersion, jarFile)) = fakeRepoConnector.uploadedFiles.find(_._2.toString.endsWith("sbt-bobby.jar"))

      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      jarFile.getFileName.toString should be("sbt-bobby.jar")
      ivyFile.getFileName.toString should be("ivy.xml")
      publishedDescriptor should not be None

      jarVersion.version.value shouldBe "0.1.1"

      val manifest = manifestFromZipFile(jarFile)


      manifest.value.getValue("Implementation-Version") shouldBe "0.1.1"
      val ivyVersionText = (XML.loadFile(ivyFile.toFile) \ "info" \ "@revision").text
      ivyVersionText shouldBe "0.1.1"

      val(md, ver) = githubReleaseBuilder.params.value
      md.sha shouldBe "gitsha"
      ver.sourceVersion.value shouldBe "0.8.1-4-ge733d26"

      githubTagObjBuilder.params.value shouldBe ((Repo("sbt-bobby"), ReleaseVersion("0.1.1"), "gitsha"))
      githubTagRefBuilder.params.value shouldBe ((Repo("sbt-bobby"), ReleaseVersion("0.1.1"), "the-tag-sha"))
    }
  }

  "buildTargetFileName" should {
    "buildTargetFileName" in {
      val coord = buildDefaultCoordinator()
      val remotePath = "/time/time_2.11-1.3.0-1-g21312cc.jar"
      val versionMapping: VersionMapping = mavenVersionMapping(artefactName = "time", releaseVersion = "1.0.0")
      val targetFileName = coord.buildTargetFileName(versionMapping, remotePath, "time_2.11-1.3.0-1-g21312cc")

      targetFileName shouldBe "time_2.11-1.0.0.jar"
    }
  }

  class MockFunction2[A, B]{
    var params:Option[(A, B)] = None

    def build:(A, B) => Try[Unit] ={
      (a, b) => {
        params = Some((a, b))
        Success(Unit)
      }
    }
  }

  class MockFunction3[A, B, C, R]{
    var params:Option[(A, B, C)] = None

    def build[R](r:R):(A, B, C) => Try[R] ={
      (a, b, c) => {
        params = Some((a, b, c))
        Success(r)
      }
    }
  }

  def tmpDir: Path = Files.createTempDirectory("test-release")

  def manifestFromZipFile(file: Path): Option[Attributes] = {
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
