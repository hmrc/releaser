package uk.gov.hmrc.releaser

import java.net.URL
import java.nio.file.{Files, Path}

import scala.util.{Failure, Success, Try}

class FileDownloader extends Logger {
  import resource._

  def url2File(url: String, targetFile: Path): Try[Path] = {
    if(targetFile.toFile.exists()){
      log.info(s"not downloading from $url as file already exists")
      Success(targetFile)
    } else {
      log.info(s"downloading $url to $targetFile")

      try {
        managed(new URL(url).openConnection().getInputStream).foreach { in =>
          Files.copy(in, targetFile)
        }

        Success(targetFile)
      } catch {
        case e: Exception => Failure(e)
      }
    }
  }
}
