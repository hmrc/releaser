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

import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Path

import uk.gov.hmrc.releaser.domain.VersionDescriptor

import scala.util.{Failure, Success, Try}

trait RepoConnector {
  def uploadArtifacts(versions: Seq[VersionDescriptor], localFiles: Map[ArtifactClassifier, Path]): Try[Unit]

  def downloadArtifacts(versions: Seq[VersionDescriptor]): Try[Map[ArtifactClassifier, Path]]

  def publish(version: VersionDescriptor): Try[Unit]
}


trait MetaConnector {

  def getRepoMetaData(repoName: String, artefactName: String): Try[Unit]

  def publish(version: VersionDescriptor): Try[Unit]

}


object Http {

  val log = new Logger()

  def url2File(url: String, targetFile: Path, mandatory: Boolean): Try[Option[Path]] = {
    log.info(s"downloading $url to $targetFile")
    try {

      val connection = new URL(url).openConnection()
      val input = connection.getInputStream
      val buffer = new Array[Byte](4096)
      var n = - 1
      val output = new FileOutputStream(targetFile.toFile)

      Stream.continually(input.read(buffer)).takeWhile(_ != -1).foreach(output.write(buffer, 0, _))

      output.close()

      Success(Some(targetFile))
    } catch {
      case e: Exception => if(mandatory) Failure(e) else {
        log.info(s"Download od file $url failed, but it's not mandatory, so ignoring it"); Success(None)
      }
    }
  }

}
