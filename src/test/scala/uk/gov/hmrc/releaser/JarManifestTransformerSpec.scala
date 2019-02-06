/*
 * Copyright 2019 HM Revenue & Customs
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
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util
import java.util.jar
import java.util.jar.Attributes
import java.util.zip.ZipFile

import com.google.common.io.ByteStreams
import org.scalatest._

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

class JarManifestTransformerSpec extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues with TryValues {

  val timeJarPath = new File(this.getClass.getResource("/time/uk/gov/hmrc/time_2.11/1.3.0-1-g21312cc/time_2.11-1.3.0-1-g21312cc.jar").toURI).toPath

  var transformer:JarManifestTransformer = _
  val candidate_1_3_0_1_g21312cc = ReleaseCandidateVersion("1.3.0-1-g21312cc")
  val release_1_4_0 = ReleaseVersion("1.4.0")
  var tmpDir:Path = _

  override def beforeEach() {
    tmpDir = Files.createTempDirectory("tmp")
    transformer = new JarManifestTransformer()
  }

  "the transformer" should {

    "not transform any file metadata other than the META-INF/MANIFEST.MF file" in {

      val outFile = transformer(timeJarPath, "time", candidate_1_3_0_1_g21312cc, release_1_4_0, tmpDir.resolve("time-1.4.0.jar")).success.get

      val inTimes = zipFileTimes(timeJarPath)
      val outTimes = zipFileTimes(outFile)

      inTimes - "META-INF/MANIFEST.MF" shouldBe outTimes - "META-INF/MANIFEST.MF"
    }

    "not transform any timestamps including the META-INF/MANIFEST.MF file" in {

      val outFile = transformer(timeJarPath, "time", candidate_1_3_0_1_g21312cc, release_1_4_0, tmpDir.resolve("time-1.4.0.jar")).success

      zipFileTimes(outFile.get) shouldBe zipFileTimes(timeJarPath)
    }

    "not transform any files other than the META-INF/MANIFEST.MF file" in {

      val outFile = transformer(timeJarPath, "time", candidate_1_3_0_1_g21312cc, release_1_4_0, tmpDir.resolve("time-1.4.0.jar")).success

      md5OfJarEntries(outFile.get) - "META-INF/MANIFEST.MF" shouldBe md5OfJarEntries(timeJarPath) - "META-INF/MANIFEST.MF"
    }

    "transform the manifest of a zip file and name the generated jar file to time-1.4.0.jar" in {

      val outFile = transformer(timeJarPath, "time", candidate_1_3_0_1_g21312cc, release_1_4_0, tmpDir.resolve("time-1.4.0.jar")) match {
        case Success(f) => Success(f)
        case Failure(f) => println(f); Failure(f)
      }
      
      val manifest = manifestFromJarFile(outFile.get.toFile).value
      
      outFile shouldBe Success(tmpDir.resolve("time-1.4.0.jar"))
      manifest.getValue("Implementation-Version") shouldBe "1.4.0"
      manifest.getValue("Git-Describe") shouldBe "1.4.0"
      manifest.getValue("Specification-Version") shouldBe "1.4.0"
    }
  }


  def zipFileTimes(zip: Path): Map[String, Long] = {
    val zipFile: ZipFile = new ZipFile(zip.toFile)

    zipFile.entries().toList.map { ze =>
      ze.getName -> ze.getTime
    }.toMap
  }

  def md5OfJarEntries(jarIn: Path): Map[String, String] = {
    val zipFile: ZipFile = new ZipFile(jarIn.toFile)

    val name2md5s = zipFile.entries().toList.map { ze =>
      ze.getName -> util.Arrays.toString(MessageDigest.getInstance("MD5").digest(ByteStreams.toByteArray(zipFile.getInputStream(ze))))
    }

    zipFile.close()
    name2md5s.toMap
  }

  def manifestFromJarFile(file: File): Option[Attributes] = {
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
