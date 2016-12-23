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
          Files.createDirectories(targetFile.getParent)
          Files.copy(in, targetFile)
        }

        Success(targetFile)
      } catch {
        case e: Exception => Failure(e)
      }
    }
  }
}
