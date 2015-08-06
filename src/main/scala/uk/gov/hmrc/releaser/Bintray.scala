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

import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.libs.ws.{DefaultWSClientConfig, WSAuthScheme, WSResponse}
import play.api.mvc.Results
import uk.gov.hmrc.releaser.domain.{BintrayPaths, PathBuilder, VersionDescriptor}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class BintrayMetaConnector(bintrayHttp: BintrayHttp) extends MetaConnector {

  def getRepoMetaData(repoName: String, artefactName: String): Try[Unit] = {
    val url = BintrayPaths.metadata(repoName, artefactName)
    bintrayHttp.get(url).map { _ => Unit}
  }

  def publish(version: VersionDescriptor): Try[Unit] = {
    val url = BintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }

}

object BintrayRepoConnector {
  def apply(workDir: Path, bintrayHttp: BintrayHttp)(f: PathBuilder): BintrayRepoConnector = {
    new BintrayRepoConnector(workDir, bintrayHttp, f)
  }
}

class BintrayRepoConnector(workDir: Path, bintrayHttp: BintrayHttp, bintrayPaths: PathBuilder) extends RepoConnector {

  val log = new Logger()


  override def uploadArtifacts(versions: Seq[VersionDescriptor], localFiles: Map[ArtifactClassifier, Path]): Try[Unit] = Try {
      versions.map { version =>
        localFiles.get(version.classifier).map {fileToUpload =>
          val url = bintrayPaths.artifactUploadFor(version)
          bintrayHttp.putFile(version, fileToUpload, url)
        }
      }.map(_.get)
    }

  override def downloadArtifacts(versions: Seq[VersionDescriptor]): Try[Map[ArtifactClassifier, Path]] = Try {
    val localPaths = versions.map { version =>

      val fileName = bintrayPaths.artifactFilenameFor(version)
      val url = bintrayPaths.artifactDownloadFor(version)
      downloadFile(url, fileName, version.classifier.mandatory).map (df => df.map( f => version.classifier -> f) )
    }.map(_.get).flatten

    localPaths.toMap
  }

  override def publish(version: VersionDescriptor): Try[Unit] = {
    val url = bintrayPaths.publishUrlFor(version)
    bintrayHttp.emptyPost(url)
  }

  private def downloadFile(url: String, fileName: String, mandatory: Boolean): Try[Option[Path]] = {
    val targetFile = workDir.resolve(fileName)

    Http.url2File(url, targetFile) map { unit => Some(targetFile)} recover {
      case _ if !mandatory => None
    }
  }
}

class BintrayHttp(creds: ServiceCredentials) {

  val log = new Logger()

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig).build())


  def apiWs(url: String) = ws.url(url)
    .withAuth(
      creds.user, creds.pass, WSAuthScheme.BASIC)
    .withHeaders("content-type" -> "application/json")

  def emptyPost(url: String): Try[Unit] = {
    log.info(s"posting file to $url")

    val call = apiWs(url).post(Results.EmptyContent())

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def get[A](url: String): Try[String] = {
    log.info(s"getting file from $url")

    val call = apiWs(url).get()

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(result.body)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def putFile(version: VersionDescriptor, file: Path, url: String): Try[Unit] = {
    log.info(s"version $version")
    log.info(s"putting file '$file' to $url")
    log.info(s"bintray user ${System.getenv("BINTRAY_USER")}")

    val call = apiWs(url)
      .withHeaders(
        "X-Bintray-Package" -> version.artefactName,
        "X-Bintray-Version" -> version.version.value)
      .put(file.toFile)

    val result: WSResponse = Await.result(call, Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText}")

    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }
}
