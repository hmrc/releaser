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

import java.net.URL
import java.nio.file.{Paths, Files, Path}
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
import org.mockito.Mockito._
import scala.xml.XML

class CoordinatorSpecs extends WordSpec with Matchers with OptionValues with MockitoSugar{

  "the coordinator" should {

    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {

      val fakeRepoConnector = Builders.buildConnector()
//      val fakeMetaConnector = Builders.buildMetaConnector()

      val pathBuilder = new BintrayMavenPaths()

//      val releaser = new Releaser(Paths.get("/tmp"), Seq(RepositoryFlavour(pathBuilder, "release-candidates", "releases")), mockConnector)
      val coordinator = new Coordinator(Paths.get("/tmp"), fakeRepoConnector, pathBuilder)

      val source = VersionDescriptor(repo = "release-candidates", "time", "2.11", "1.3.0-1-g21312cc")
      val target = VersionDescriptor(repo = "releases", "time", "2.11", "0.9.9")


      val uploadedURLs = coordinator.start(source, target) match {
        case Failure(e) => fail(e)
        case Success(urls) => urls
      }

      val Some((jarVersion, jarFile)) = fakeRepoConnector.lastUploadedJar
      val Some((pomVersion, pomFile)) = fakeRepoConnector.lastUploadedPom
      val publishedDescriptor = fakeRepoConnector.lastPublishDescriptor

      jarFile.getFileName.toString should endWith(".jar")
      pomFile.getFileName.toString should endWith(".pom")
      publishedDescriptor should not be None

      val manifest = manifestFromZipFile(jarFile)

      jarVersion shouldBe VersionDescriptor("releases", "time", "2.11", "0.9.9")
      pomVersion shouldBe VersionDescriptor("releases", "time", "2.11", "0.9.9")

      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
      val pomVersionText = (XML.loadFile(pomFile.toFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
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
