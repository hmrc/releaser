/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.releaser.github

import java.util.concurrent.TimeUnit

import play.api.libs.json.JsValue
import play.api.libs.ws._
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import uk.gov.hmrc.{Logger, ServiceCredentials}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class GithubHttp(cred: ServiceCredentials) extends Logger {

  val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new DefaultWSClientConfig).build())

  def buildCall(method:String, url:String, body:Option[JsValue] = None):WSRequestHolder={
    log.debug(s"github client_id ${cred.user.takeRight(5)}")
    log.debug(s"github client_secret ${cred.pass.takeRight(5)}")

    val req = ws.url(url)
      .withMethod(method)
      .withAuth(cred.user, cred.pass, WSAuthScheme.BASIC)
      .withQueryString("client_id" -> cred.user, "client_secret" -> cred.pass)
      .withHeaders("content-type" -> "application/json")

    body.map { b =>
      req.withBody(b)
    }.getOrElse(req)
  }

  def callAndWait(req:WSRequestHolder): WSResponse = {
    log.info(s"${req.method} with ${req.url}")
    val result: WSResponse = Await.result(req.execute(), Duration.apply(1, TimeUnit.MINUTES))
    log.info(s"${req.method} with ${req.url} result ${result.status} - ${result.statusText}")
    result
  }

  def get(url:String): Try[Unit] = {
    val result = callAndWait(buildCall("GET", url))
    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Github. Got status ${result.status}: ${result.body}"))
    }
  }

  def post[A](responseBuilder:(WSResponse) => Try[A])(url:String, body:JsValue): Try[A] = {
    log.debug("github url: " + url)
    log.debug("github body: " + body)

    val result = callAndWait(buildCall("POST", url, Some(body)))
    result.status match {
      case s if s >= 200 && s < 300 => responseBuilder(result)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Github. Got status ${result.status}: ${result.body}"))
    }
  }

  def postUnit(url:String, body:JsValue): Try[Unit] = {
    post[Unit](_ => Success(Unit))(url, body)
  }
}
