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

import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import play.api.libs.ws.{WSResponse, WSAuthScheme, DefaultWSClientConfig}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.mvc.Results

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.sys.process._


trait RepoConnector{
  def uploadJar(version: VersionDescriptor, jarFile:Path):Try[URL]
  def downloadJar(version:VersionDescriptor):Try[Path]
  def uploadPom(version: VersionDescriptor, pomPath:Path):Try[URL]
  def downloadPom(version:VersionDescriptor):Try[Path]
  def publish(version: VersionDescriptor):Try[Unit]
}

class BintrayHttp{

  val log = new Logger()

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig).build())


  def apiWs(url:String) = ws.url(url)
    .withAuth(
      System.getenv("BINTRAY_USER"),
      System.getenv("BINTRAY_PASS"),
      WSAuthScheme.BASIC)
    .withHeaders(
      "content-type" -> "application/json")

  def emptyPost(url:String): Try[Unit] = {
    log.info(s"posting file to $url")

    val call = apiWs(url).post(Results.EmptyContent())

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def get[A](url:String): Try[String] ={
    log.info(s"getting file from $url")

    val call = apiWs(url).get()

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(result.body)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def putFile(version: VersionDescriptor, file: Path, url: String): Try[URL] = {
    log.info(s"version $version")
    log.info(s"putting file to $url")
    log.info(s"bintray user ${System.getenv("BINTRAY_USER")}")

    val call = apiWs(url)
      .withHeaders(
        "X-Bintray-Package" -> version.artefactName,
        "X-Bintray-Version" -> version.version)
      .put(file.toFile)

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }
}

trait MetaConnector{

  def getRepoMetaData(repoName:String, artefactName: String):Try[Unit]

  def publish(version: VersionDescriptor):Try[Unit]

}


class BintrayMetaConnector(bintrayHttp:BintrayHttp) extends MetaConnector{

  def getRepoMetaData(repoName:String, artefactName: String):Try[Unit]={
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => url }
  }

  def publish(version: VersionDescriptor):Try[Unit]={
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url).map { _ => url }
  }

}

object BintrayRepoConnector{
  def apply(workDir:Path, bintrayHttp:BintrayHttp)(f:PathBuilder):BintrayRepoConnector = {
    new BintrayRepoConnector(workDir, bintrayHttp, f)
  }
}

class BintrayRepoConnector(workDir:Path, bintrayHttp:BintrayHttp, bintrayPaths:PathBuilder) extends RepoConnector{

  val log = new Logger()

  def uploadPom(version: VersionDescriptor, pomFile:Path):Try[URL] ={
    val url = bintrayPaths.pomUploadFor(version)
    bintrayHttp.putFile(version, pomFile, url)
  }

  def uploadJar(version: VersionDescriptor, jarFile:Path):Try[URL] = {
    val url = bintrayPaths.jarUploadFor(version)
    bintrayHttp.putFile(version, jarFile, url)
  }

  def publish(version: VersionDescriptor):Try[Unit]={
    val url = bintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }

  def downloadPom(version:VersionDescriptor):Try[Path]={

    val fileName = bintrayPaths.pomFilenameFor(version)
    val pomUrl = bintrayPaths.pomDownloadUrlFor(version)

    downloadFile(pomUrl, fileName)
  }

  def downloadJar(version:VersionDescriptor):Try[Path] = {

    val fileName = bintrayPaths.jarFilenameFor(version)
    val artefactUrl = bintrayPaths.jarDownloadFor(version)

    downloadFile(artefactUrl, fileName)
  }

  def downloadFile(url:String, fileName:String):Try[Path]={
    val targetFile = workDir.resolve(fileName)

    Http.url2File(url, targetFile) map { unit => targetFile }
  }

}

object Http{

  val log = new Logger()

  def url2File(url: String, targetFile: Path): Try[Unit] = Try {
    log.info(s"downloading $url to $targetFile")
    new URL(url) #> targetFile.toFile !!
  }
}
