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

import java.io._
import java.nio.file.Path

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.FileUtils
import org.scalatest._
import uk.gov.hmrc.releaser.Builders

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

class TgzTransformerSpec extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues with TryValues{

  val tgzPath = new File(this.getClass.getResource("/help-frontend/help-frontend_2.11-1.26.0-3-gd7ed03c.tgz").toURI).toPath

  var transformer:TgzTransformer = _
  val candidate_1_26_0_3_gd7ed03c = ReleaseCandidateVersion("1.26.0-3-gd7ed03c")
  val release_1_4_0 = ReleaseVersion("1.4.0")
  var tmpDir:Path = _

  override def beforeEach(){
    tmpDir = Builders.tempDir()
    transformer = new TgzTransformer()
    FileUtils.copyFileToDirectory(tgzPath.toFile, tmpDir.toFile)
  }

  override def afterEach(){
    FileUtils.deleteDirectory(tmpDir.toFile)
  }

  "the transformer" should {

    "decompress the tgz, rename the main folder and compress it back" in {

      val inFile = new File(tmpDir.toFile, tgzPath.getFileName.toString).toPath
      val targetFilePath = tmpDir.resolve("help-frontend-1.4.0.tgz")

      val originalTarEntries = listTgzEntries(inFile)
      assertTarEntry(originalTarEntries, "./help-frontend-1.26.0-3-gd7ed03c/")
      assertTarEntry(originalTarEntries, "./help-frontend-1.4.0/", exists = false)

      val outFileTry = transformer(inFile, "help-frontend", candidate_1_26_0_3_gd7ed03c, release_1_4_0, targetFilePath)
      outFileTry match {
        case Success(outFile) => {
          val tarEntries = listTgzEntries(targetFilePath)
          assertTarEntry(tarEntries, "./help-frontend-1.26.0-3-gd7ed03c/", exists = false)
          assertTarEntry(tarEntries, "./help-frontend-1.4.0/")
        }
        case Failure(e) => fail("Caught exception: " + e.getMessage, e)
      }


    }
  }

  private def listTgzEntries(localTgzFile: Path) : List[ArchiveEntry] =  {
    val bytes = new Array[Byte](2048)
    val fin = new BufferedInputStream(new FileInputStream(localTgzFile.toFile))
    val gzIn = new GzipCompressorInputStream(fin)
    val tarIn = new TarArchiveInputStream(gzIn)

    val entries = ListBuffer[ArchiveEntry]()

    Iterator continually tarIn.getNextEntry takeWhile (null !=) foreach { tarEntry =>
      entries += tarEntry
    }

    tarIn.close()

    entries.toList

  }

  private def assertTarEntry(tarEntries: List[ArchiveEntry], entryName: String, exists: Boolean = true) = {
    tarEntries.exists (_.getName == entryName) shouldBe exists
  }
}
