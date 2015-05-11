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
import java.util.concurrent.TimeUnit

import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import uk.gov.hmrc.releaser.domain.{ArtefactMetaData, VersionMapping}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object GithubApi{

  val githubDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTime
  val releaseMessageDateTimeFormat = DateTimeFormat.longDateTime()


  val taggerName  = "hmrc-web-operations"
  val taggerEmail = "hmrc-web-operations@digital.hmrc.gov.uk"

  case class GitRelease(
                         name:String,
                         tag_name:String,
                         body:String,
                         target_commitish:String,
                         draft:Boolean,
                         prerelease:Boolean)

  object GitRelease{
    implicit val formats = Json.format[GitRelease]
  }


  val logger = new Logger()

  def verifyCommit(getter:(String) => Try[Unit])(repo:String, sha:String): Try[Unit] ={
    getter(buildCommitGetUrl(repo, sha))
  }

  def postTag(tagger: (String, JsValue) => Try[Unit])(releaserVersion:String)(artefactMd: ArtefactMetaData, v: VersionMapping): Try[Unit] = {
    logger.debug("publishing meta data " + artefactMd + " version mapping " + v)

    val url = buildTagPostUrl(v.artefactName)

    val message = buildMessage(
      releaserVersion,
      v.sourceVersion,
      artefactMd)

    val body = buildTagBody(message, v.targetVersion, artefactMd)

    logger.debug("github url: " + url)
    logger.debug("github body: " + body)

    tagger(url, body)
  }

  def buildTagBody(message:String, targetVersion:String, artefactMd:ArtefactMetaData):JsValue={
    val tagName = "v" + targetVersion

    Json.toJson(
      GitRelease(targetVersion, tagName, message, artefactMd.sha, draft = false, prerelease = false))
  }

  def buildMessage(
                    releaserVersion:String,
                    sourceVersion:String,
                    artefactMetaData: ArtefactMetaData)={


    s"""
        |Release and tag created by [Releaser](https://github.com/hmrc/releaser) version $releaserVersion.
        |
        |Release candidate  : $sourceVersion
        |
        |Last commit sha    : ${artefactMetaData.sha}
        |Last commit author : ${artefactMetaData.commitAuthor}
        |Last commit time   : ${DateTimeFormat.longDateTime().print(artefactMetaData.commitDate)}""".stripMargin
  }

  def buildCommitGetUrl(artefactName:String, sha:String)={
    s"https://api.github.com/repos/hmrc/$artefactName/git/commits/$sha"
  }
  
  def buildTagPostUrl(artefactName:String)={
    s"https://api.github.com/repos/hmrc/$artefactName/releases"
  }
}

class GithubHttp(cred:ServiceCredentials){
  val log = new Logger()

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

    log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")

    result
  }

  def get(url:String): Try[Unit] = {
    val result = callAndWait(buildCall("GET", url))
    result.status match {
      case s if s >= 200 && s < 300 => Success(Unit)
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Github. Got status ${result.status}: ${result.body}"))
    }
  }

  def post(url:String, body:JsValue): Try[Unit] = {
    val result = callAndWait(buildCall("POST", url, Some(body)))
    result.status match {
      case s if s >= 200 && s < 300 => Success(new URL(url))
      case _@e => Failure(new scala.Exception(s"Didn't get expected status code when writing to Github. Got status ${result.status}: ${result.body}"))
    }
  }
}
