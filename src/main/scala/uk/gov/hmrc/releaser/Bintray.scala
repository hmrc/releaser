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

package uk.gov.hmrc.releaser

import java.net.{HttpURLConnection, URL}
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import play.api.libs.json.{JsValue, JsPath, Json}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.libs.ws.{DefaultWSClientConfig, WSAuthScheme, WSResponse}
import play.api.mvc.Results
import uk.gov.hmrc.releaser.domain.{BintrayPaths, PathBuilder, VersionDescriptor}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class BintrayMetaConnector(bintrayHttp:BintrayHttp) extends MetaConnector{

  def getRepoMetaData(repoName:String, artefactName: String):Try[Unit]={
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => Unit}
  }

  def publish(version: VersionDescriptor):Try[Unit]={
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }
}

object BintrayRepoConnector{
  def apply(workDir:Path, bintrayHttp:BintrayHttp)(f:PathBuilder):BintrayRepoConnector = {
    new BintrayRepoConnector(workDir, bintrayHttp, f)
  }
}

class BintrayRepoConnector(workDir:Path, bintrayHttp:BintrayHttp, bintrayPaths:PathBuilder) extends RepoConnector{

  val log = new Logger()

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

  def downloadJar(version:VersionDescriptor):Try[Path] = {

    val fileName = bintrayPaths.jarFilenameFor(version)
    val artefactUrl = bintrayPaths.jarDownloadFor(version)

    downloadFile(artefactUrl, fileName)
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


class BintrayHttp(creds:ServiceCredentials){

  val log = new Logger()

  private def getTimeoutPropertyOptional(key: String) = Option(System.getProperty(key)).map(_.toLong * 1000)

  def wsClientConfig = new DefaultWSClientConfig(
    connectionTimeout = getTimeoutPropertyOptional("wsclient.timeout.connection"),
    idleTimeout = getTimeoutPropertyOptional("wsclient.timeout.idle"),
    requestTimeout = getTimeoutPropertyOptional("wsclient.timeout.request")
  )

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(wsClientConfig).build())


  def apiWs(url:String) = ws.url(url)
    .withAuth(
      creds.user, creds.pass, WSAuthScheme.BASIC)
    .withHeaders("content-type" -> "application/json")

  def emptyPost(url:String): Try[Unit] = {
    log.info(s"posting file to $url")

    val call = apiWs(url).post(Results.EmptyContent())

    val result: WSResponse = Await.result(call, Duration.apply(5, TimeUnit.MINUTES))

    //log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def get[A](url:String): Try[String] ={
    log.info(s"getting file from $url")

    val call = apiWs(url).get()

    val result: WSResponse = Await.result(call, Duration.apply(5, TimeUnit.MINUTES))

    //log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(result.body)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = {
    log.info(s"version $version")
    log.info(s"putting file to $url")

    val call = apiWs(url)
      .withHeaders(
        "X-Bintray-Package" -> version.artefactName,
        "X-Bintray-Version" -> version.version.value)
      .put(file.toFile)

    val result: WSResponse = Await.result(call, Duration.apply(6, TimeUnit.MINUTES))

    //log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }
}
