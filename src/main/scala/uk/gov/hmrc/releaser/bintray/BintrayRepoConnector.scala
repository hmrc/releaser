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

package uk.gov.hmrc.releaser.bintray

import java.net.{HttpURLConnection, URL}
import java.nio.file.Path

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.releaser.domain.{PathBuilder, VersionDescriptor}
import uk.gov.hmrc.releaser.{Http, Logger, RepoConnector}

import scala.util.{Failure, Success, Try}

object BintrayRepoConnector{
  def apply(workDir:Path, bintrayHttp:BintrayHttp)(f:PathBuilder):BintrayRepoConnector = {
    new BintrayRepoConnector(workDir, bintrayHttp, f)
  }
}

class BintrayRepoConnector(workDir:Path, bintrayHttp:BintrayHttp, bintrayPaths:PathBuilder) extends RepoConnector with Logger {

  def publish(version: VersionDescriptor):Try[Unit]={
    val url = bintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }

  override def verifyTargetDoesNotExist(version:VersionDescriptor):Try[Unit]={
    val artefactUrl = bintrayPaths.jarDownloadFor(version)

    val conn = new URL(artefactUrl).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("HEAD")
    conn.connect()

    conn.getResponseCode match {
      case 200 => Failure(new IllegalArgumentException(s"${version.artefactName} ${version.version} already exists"))
      case _ => Success()
    }
  }

  def findJar(version:VersionDescriptor):Option[Path] = {
    val fileName = bintrayPaths.jarFilenameFor(version)
    val artefactUrl = bintrayPaths.jarDownloadFor(version)

    downloadFile(artefactUrl, fileName) match {
      case Success(x) => Some(x)
      case Failure(y) => None
    }
  }

  def downloadFile(version:VersionDescriptor, fileName:String):Try[Path] = {
    val artefactUrl = bintrayPaths.fileDownloadFor(version, fileName)
    downloadFile(artefactUrl, fileName)
  }

  def uploadFile(version:VersionDescriptor, filePath:Path):Try[Unit]={
    val url = bintrayPaths.fileUploadFor(version, filePath.getFileName.toString)
    bintrayHttp.putFile(version, filePath, url)
  }

  private def downloadFile(url:String, fileName:String):Try[Path]={
    val targetFile = workDir.resolve(fileName)

    Http.url2File(url, targetFile) map { unit => targetFile }
  }

  override def findFiles(version: VersionDescriptor): Try[List[String]] = {

    val url = bintrayPaths.fileListUrlFor(version)
    bintrayHttp.get(url).map { st =>
      val fileNames: Seq[JsValue] = Json.parse(st).\\("name")
      fileNames.map(_.as[String]).toList
    }
  }
}



