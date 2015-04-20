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
import java.util
import java.util.Map.Entry
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile
import scala.collection.JavaConversions._

import org.scalatest.{OptionValues, Matchers, WordSpec}
import scala.util.{Success, Try}

class ReleaserSpecs extends WordSpec with Matchers with OptionValues{

  "the releaser" should {
    "release version 0.8.0 when given the inputs 'bobby', '0.8.1-4-ge733d26' and 'patch' as the artefact, release candidate and release type" ignore {
      Releaser.main(Array("sbt-bobby", "0.8.1-4-ge733d26", "patch")) shouldBe 0
    }

    "release version 0.9.9 when given the inputs 'time', '1.3.0-1-g21312cc' and 'patch' as the artefact, release candidate and release type" in {

      val fakeConnector = new Connector(){

        var lastUploadedVersion:Option[(VersionDescriptor, File)] = None

        override def download(version: VersionDescriptor): Try[File] = {
          Success {
            new File(this.getClass.getResource("/time/time_2.11-1.3.0-1-g21312cc.jar").toURI) }
        }

        override def upload(version: VersionDescriptor, jarFile: File): Try[Unit] = {
          lastUploadedVersion = Some(version -> jarFile)
          Success(Unit)
        }
      }

      val releaser = new Releaser(fakeConnector, "release-candidates", "releases")
      releaser.release("time", "1.3.0-1-g21312cc", "0.9.9")

      val Some((v, file)) = fakeConnector.lastUploadedVersion
      val manifest = manfestFromZipFile(file)

      v shouldBe VersionDescriptor("releases", "time", "2.11", "0.9.9")
//      manifest.value.getValue("Implementation-Version") shouldBe "0.9.9"
    }
  }

  def manfestFromZipFile(file: File): Option[Attributes] = {
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
