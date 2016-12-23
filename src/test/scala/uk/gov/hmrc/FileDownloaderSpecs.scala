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

package uk.gov.hmrc

import java.net.URL
import java.nio.file.{Files, Path}

import org.scalatest._

import scala.util.Success

class FileDownloaderSpecs extends WordSpec with Matchers with TryValues with BeforeAndAfterAll{

  val tempDir = "source-dir"

  def createSourceUrlAndTargetPath:(URL, Path)={
    val tmpDir = Files.createTempDirectory("source-dir")
    val sourceFile = tmpDir.resolve("in.txt")
    Files.createFile(sourceFile)
    val url = sourceFile.toFile.toURI.toURL

    (url, tmpDir.resolve("subdir/out.txt"))
  }

  "File Downloader" should{

    "download a file and return the path" in {
      val downloader = new FileDownloader
      val (url, targetFile) = createSourceUrlAndTargetPath

      downloader.url2File(url.toString, targetFile) shouldBe Success(targetFile)
    }

    "not download a file if the file already exists but return the path" in {
      val downloader = new FileDownloader
      val (url, targetFile) = createSourceUrlAndTargetPath

      Files.createDirectories(targetFile.getParent)
      Files.createFile(targetFile)

      downloader.url2File(url.toString, targetFile) shouldBe Success(targetFile)
    }
  }

}
