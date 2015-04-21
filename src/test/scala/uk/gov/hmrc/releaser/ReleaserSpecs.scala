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

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

class ReleaserSpecs extends WordSpec with Matchers with OptionValues{

  "the releaser" should {

    def buildConnector() = new Connector(){

      var lastUploadedJar:Option[(VersionDescriptor, File)] = None
      var lastUploadedPom:Option[(VersionDescriptor, File)] = None
      var lastPublishDescriptor:Option[VersionDescriptor] = None

      override def downloadJar(version: VersionDescriptor): Try[File] = {
        Success {
          new File(this.getClass.getResource("/time/time_2.11-1.3.0-1-g21312cc.jar").toURI) }
      }

      override def uploadJar(version: VersionDescriptor, jarFile: File): Try[URL] = {
        lastUploadedJar = Some(version -> jarFile)
        Success(new URL("http://the-url-we-uploaded-to.org"))
      }

      override def uploadPom(version: VersionDescriptor, file: File): Try[URL] = {
        lastUploadedPom = Some(version -> file)
        Success(new URL("http://the-url-we-uploaded-to.org"))
      }

      override def downloadPom(version: VersionDescriptor): Try[File] = {
        Success {
          new File(this.getClass.getResource("/time/time_2.11-1.3.0-1-g21312cc.pom").toURI) }
      }

      override def publish(version: VersionDescriptor): Try[URL] = {
        lastPublishDescriptor = Some(version)
        Success(new URL("http://the-url-we-uploaded-to.org"))
      }
    }

    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {

      val fakeConnector = buildConnector()

      val pathBuilder = new BintrayMavenPaths()

      val releaser = new Releaser(fakeConnector, tmpDir, pathBuilder, "release-candidates", "releases")

      val uploadedURLs = releaser.publishNewVersion("time", "1.3.0-1-g21312cc", "0.9.9") match {
        case Failure(e) => fail(e)
        case Success(urls) => urls
      }

      val Some((jarVersion, jarFile)) = fakeConnector.lastUploadedJar
      val Some((pomVersion, pomFile)) = fakeConnector.lastUploadedPom
      val publishedDescriptor = fakeConnector.lastPublishDescriptor

      jarFile.getName should endWith(".jar")
      pomFile.getName should endWith(".pom")
      publishedDescriptor should not be None

      val manifest = manifestFromZipFile(jarFile)

      jarVersion shouldBe VersionDescriptor("releases", "time", "2.11", "0.9.9")
      pomVersion shouldBe VersionDescriptor("releases", "time", "2.11", "0.9.9")

      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
      val pomVersionText = (XML.loadFile(pomFile) \ "version").text
      pomVersionText shouldBe "0.9.9"
    }
  }

  def tmpDir: File = Files.createTempDirectory("test-release").toFile

  def manifestFromZipFile(file: File): Option[Attributes] = {
    val zipFile: ZipFile = new ZipFile(file)

    zipFile.entries().toList.find { ze =>
      ze.getName == "META-INF/MANIFEST.MF"
    }.flatMap { ze =>
      Try(new jar.Manifest(zipFile.getInputStream(ze))).map { man =>
        man.getMainAttributes
      }.toOption
    }
  }
}
