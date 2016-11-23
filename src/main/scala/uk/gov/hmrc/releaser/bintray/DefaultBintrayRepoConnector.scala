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
import uk.gov.hmrc.{FileDownloader, Logger, ServiceCredentials}

import scala.util.{Failure, Success, Try}

object BintrayRepoConnector extends Logger {
  def apply(bintrayCreds: ServiceCredentials, workDir : Path): BintrayRepoConnector =
    new DefaultBintrayRepoConnector(workDir, new BintrayHttp(bintrayCreds), new FileDownloader())

  def dryRun(bintrayCreds: ServiceCredentials, workDir : Path) = {
    log.info("Bintray : running in dry-run mode")
    val dryRunHttp = new BintrayHttp(bintrayCreds){
      override def emptyPost(url:String): Try[Unit] = { println("BintrayHttp emptyPost DRY_RUN");Success(Unit)}
      override def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = { println("BintrayHttp putFile DRY_RUN");Success(Unit) }
    }

    new DefaultBintrayRepoConnector(workDir, dryRunHttp, new FileDownloader())
  }
}

trait BintrayRepoConnector {
  def findJar(jarFileName: String, jarUrl: String, version: VersionDescriptor): Option[Path]
  def publish(version: VersionDescriptor): Try[Unit]
  def downloadFile(url: String, fileName: String): Try[Path]
  def uploadFile(version: VersionDescriptor, filePath: Path, url: String): Try[Unit]
  def verifyTargetDoesNotExist(jarUrl: String, version: VersionDescriptor): Try[Unit]
  def findFiles(version: VersionDescriptor): Try[List[String]]
  def getRepoMetaData(repoName:String, artefactName: String): Try[Unit]
}

class DefaultBintrayRepoConnector(workDir: Path, bintrayHttp: BintrayHttp, fileDownloader: FileDownloader)
  extends BintrayRepoConnector with Logger {

  def publish(version: VersionDescriptor):Try[Unit] = {
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }

  def verifyTargetDoesNotExist(jarUrl: String, version: VersionDescriptor): Try[Unit] = {
    val conn = new URL(jarUrl).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("HEAD")
    conn.connect()

    conn.getResponseCode match {
      case 200 => Failure(new IllegalArgumentException(s"${version.artefactName} ${version.version} already exists"))
      case _ => Success()
    }
  }

  def findJar(jarFileName: String, jarUrl: String, version: VersionDescriptor): Option[Path] = {
    downloadFile(jarUrl, jarFileName) match {
      case Success(x) => Some(x)
      case Failure(y) => None
    }
  }

  def uploadFile(version: VersionDescriptor, filePath: Path, url: String): Try[Unit] = {
    bintrayHttp.putFile(version, filePath, url)
  }

  def downloadFile(url: String, fileName: String): Try[Path] = {
    val targetFile = workDir.resolve(fileName)
    fileDownloader.url2File(url, targetFile) map { unit => targetFile }
  }

  def findFiles(version: VersionDescriptor): Try[List[String]] = {
    val url = BintrayPaths.fileListUrlFor(version)
    bintrayHttp.get(url).map { st =>
      val fileNames: Seq[JsValue] = Json.parse(st).\\("name")
      fileNames.map(_.as[String]).toList
    }
  }

  def getRepoMetaData(repoName:String, artefactName: String): Try[Unit] = {
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => Unit}
  }
}



