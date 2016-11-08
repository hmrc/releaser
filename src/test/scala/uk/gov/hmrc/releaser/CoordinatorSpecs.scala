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
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import uk.gov.hmrc.releaser.domain._

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
          githubTagObjPublisher = githubTagObjBuilder.build("the-tag-sha"),
          githubTagRefPublisher = githubTagRefBuilder.build(Unit)
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

    def build(r:R):(A, B, C) => Try[R] ={
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
