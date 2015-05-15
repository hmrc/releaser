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


    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector(
        "/time/time_2.11-1.3.0-1-g21312cc.jar",
        "/time/time_2.11-1.3.0-1-g21312cc.pom")

      def fakeRepoConnectorBuilder(p: PathBuilder):RepoConnector = fakeRepoConnector

      val releaser = buildDefaultReleaser(
        repositoryFinder = successfulRepoFinder(mavenRepository),
        connectorBuilder = fakeRepoConnectorBuilder,
        artefactMetaData = ArtefactMetaData("sha", "time", DateTime.now()))

      releaser.start("time", "1.3.0-1-g21312cc", "0.9.9") match {
        case Failure(e) => fail(e)
        case _ =>
      }

      val Some((jarVersion, jarFile)) = fakeRepoConnector.lastUploadedJar
      val Some((pomVersion, pomFile)) = fakeRepoConnector.lastUploadedPom
      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      jarFile.getFileName.toString should endWith(".jar")
      pomFile.getFileName.toString should endWith(".pom")
      publishedDescriptor should not be None

      jarVersion.version shouldBe "0.9.9"

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

      releaser.start("a", "b", "c") match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
      }
    }

    "fail when the repository of an artefact isn't found" in {
      val expectedException = new scala.Exception("repo fail")

      val releaser = buildDefaultReleaser(
        repositoryFinder = (a) => Failure(expectedException)
      )

      releaser.start("a", "b", "c") match {
        case Failure(e) => e shouldBe expectedException
        case Success(s) => fail(s"Should have failed with $expectedException")
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

    "release version 0.1.1 when given the inputs 'sbt-bobby', '0.8.1-4-ge733d26' and 'patch' as the artefact, release candidate and release type" in {

      val githubReleaseBuilder = new MockFunction2[ArtefactMetaData, VersionMapping]()
      val githubTagObjBuilder = new MockFunction3[String, String, CommitSha, CommitSha]()
      val githubTagRefBuilder = new MockFunction3[String, String, CommitSha, Unit]()

      val fakeRepoConnector = Builders.buildConnector(
        "/sbt-bobby/sbt-bobby.jar",
        "/sbt-bobby/ivy.xml"
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

        releaser.start("sbt-bobby", "0.8.1-4-ge733d26", "0.1.1") match {
          case Failure(e) => fail(e)
          case _ =>
        }


      val Some((jarVersion, jarFile)) = fakeRepoConnector.lastUploadedJar
      val Some((ivyVersion, ivyFile)) = fakeRepoConnector.lastUploadedPom
      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      jarFile.getFileName.toString should be("sbt-bobby.jar")
      ivyFile.getFileName.toString should be("ivy.xml")
      publishedDescriptor should not be None

      jarVersion.version shouldBe "0.1.1"

      val manifest = manifestFromZipFile(jarFile)


      manifest.value.getValue("Implementation-Version") shouldBe "0.1.1"
      val ivyVersionText = (XML.loadFile(ivyFile.toFile) \ "info" \ "@revision").text
      ivyVersionText shouldBe "0.1.1"

      val(md, ver) = githubReleaseBuilder.params.value
      md.sha shouldBe "gitsha"
      ver.sourceVersion shouldBe "0.8.1-4-ge733d26"

      githubTagObjBuilder.params.value shouldBe ("sbt-bobby", "0.1.1", "gitsha")
      githubTagRefBuilder.params.value shouldBe ("sbt-bobby", "0.1.1", "the-tag-sha")
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
