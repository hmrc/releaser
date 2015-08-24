package uk.gov.hmrc.releaser

import java.net.URL
import java.nio.file.{Path, Files}

import org.scalatest._

import scala.util.Success

class HttpSpecs extends WordSpec with Matchers with TryValues with BeforeAndAfterAll{

  val tempDir = "source-dir"

  def createSourceUrlAndTargetPath:(URL, Path)={
    val tmpDir = Files.createTempDirectory("source-dir")
    val sourceFile = tmpDir.resolve("in.txt")
    Files.createFile(sourceFile)
    val url = sourceFile.toFile.toURI.toURL


    (url, tmpDir.resolve("out.txt"))
  }

  "Http" should{

    "download a file and return the path" in {
      val (url, targetFile) = createSourceUrlAndTargetPath

      Http.url2File(url.toString, targetFile) shouldBe Success(targetFile)
    }

    "not download a file if the file already exists but return the path" in {
      val (url, targetFile) = createSourceUrlAndTargetPath
      Files.createFile(targetFile)

      Http.url2File(url.toString, targetFile) shouldBe Success(targetFile)
    }
  }

}
