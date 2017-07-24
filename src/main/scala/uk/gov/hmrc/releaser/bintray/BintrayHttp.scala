/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.stream.Materializer
import uk.gov.hmrc.{Logger, ServiceCredentials}

class BintrayHttp(creds:ServiceCredentials)(implicit val materializer: Materializer) extends Logger {

  import java.net.URL
  import java.nio.file.Path
  import java.util.concurrent.TimeUnit

  import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
  import play.api.libs.ws.{WSAuthScheme, WSClientConfig, WSResponse}
  import play.api.mvc.Results

  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import scala.util.{Failure, Success, Try}

  private def getTimeoutPropertyOptional(key: String) = {
    import scala.concurrent.duration._
    Option(System.getProperty(key)).map(_.toLong * 1000).get.milliseconds
  }

  def wsClientConfig = WSClientConfig(connectionTimeout = getTimeoutPropertyOptional("wsclient.timeout.connection"),
    idleTimeout = getTimeoutPropertyOptional("wsclient.timeout.idle"),
    requestTimeout = getTimeoutPropertyOptional("wsclient.timeout.request"))

  val ws = AhcWSClient(AhcWSClientConfig(wsClientConfig))

  def apiWs(url:String) = ws.url(url)
    .withAuth(
      creds.user, creds.pass, WSAuthScheme.BASIC)
    .withHeaders("content-type" -> "application/json")

  def emptyPost(url:String): Try[Unit] = {
    log.info(s"posting file to $url")

    val call = apiWs(url).post(Results.EmptyContent())
    val result: WSResponse = Await.result(call, Duration.apply(5, TimeUnit.MINUTES))

    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }

  def get[A](url:String): Try[String] ={
    log.info(s"getting file from $url")

    val call = apiWs(url).get()
    val result: WSResponse = Await.result(call, Duration.apply(5, TimeUnit.MINUTES))

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
        "X-Bintray-Version" -> version.version)
      .put(file.toFile)

    val result: WSResponse = Await.result(call, Duration.apply(6, TimeUnit.MINUTES))

    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Bintray. Got status ${result.status}: ${result.body}"))
    }
  }
}
