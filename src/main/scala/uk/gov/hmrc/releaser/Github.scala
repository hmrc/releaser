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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import uk.gov.hmrc.releaser.domain._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object GithubApi{

  val githubDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTime
  val githubTagDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTimeNoMillis
  val releaseMessageDateTimeFormat = DateTimeFormat.longDateTime()


  val taggerName  = "hmrc-web-operations"
  val taggerEmail = "hmrc-web-operations@digital.hmrc.gov.uk"

  case class Tagger(name:String, email:String, date:String)
  case class TagObject(tag:String, message:String, `object`:String, tagger: Tagger, `type`:String = "commit")
  case class TagRef(ref:String, sha:String)
  case class TagRefResponse(sha:String)

  case class GitRelease(
                         name:String,
                         tag_name:String,
                         body:String,
                         draft:Boolean,
                         prerelease:Boolean)

  object Tagger{
    implicit val formats = Json.format[Tagger]
  }

  object TagObject{
    implicit val formats = Json.format[TagObject]
  }

  object TagRef{
    implicit val formats = Json.format[TagRef]
  }

  object TagRefResponse{
    implicit val formats = Json.format[TagRefResponse]
  }

  object GitRelease{
    implicit val formats = Json.format[GitRelease]
  }

  val shaFromResponse = (r:WSResponse) => Success(r.json.as[TagRefResponse].sha)

  val logger = new Logger()

  def verifyCommit(getter:(Url) => Try[Unit])(repo:Repo, sha:CommitSha): Try[Unit] ={
    getter(buildCommitGetUrl(repo, sha))
  }

  def createAnnotatedTagRef(tagger: (Url, JsValue) => Try[Unit])(releaserVersion:String)
                           (repo:Repo, targetVersion:ReleaseVersion, commitSha:CommitSha): Try[Unit] = {
    logger.debug("creating annotated tag ref from " + targetVersion + " version mapping " + targetVersion)

    val url = buildAnnotatedTagRefPostUrl(repo)

    val body = buildTagRefBody(targetVersion, commitSha)

    logger.debug("github url: " + url)
    logger.debug("github body: " + body)

    tagger(url, body)
  }
  
  def createAnnotatedTagObject(tagger: (Url, JsValue) => Try[CommitSha])(releaserVersion:String)
                              (repo:Repo, targetVersion:ReleaseVersion, commitSha:CommitSha): Try[CommitSha] = {
    logger.debug("creating annotated tag object from " + targetVersion + " version mapping " + targetVersion)

    val url = buildAnnotatedTagObjectPostUrl(repo)

    val body = buildTagObjectBody("tag of " + targetVersion, targetVersion, new DateTime(), commitSha)

    logger.debug("github url: " + url)
    logger.debug("github body: " + body)

    tagger(url, body)
  }

  def createRelease(tagger: (Url, JsValue) => Try[Unit])(releaserVersion:String)
                   (artefactMd: ArtefactMetaData, v: VersionMapping): Try[Unit] = {
    logger.debug("creating release from " + artefactMd + " version mapping " + v)

    val url = buildReleasePostUrl(v.artefactName)

    val message = buildMessage(v.artefactName, v.targetVersion, releaserVersion, v.sourceVersion, artefactMd)

    val body = buildReleaseBody(message, v.targetVersion)

    logger.debug("github url: " + url)
    logger.debug("github body: " + body)

    tagger(url, body)
  }

  def buildTagRefBody(targetVersion: ReleaseVersion, commitSha: CommitSha): JsValue = {
    val tagName = "refs/tags/v" + targetVersion.value
    Json.toJson(TagRef(tagName, commitSha))
  }

  def buildTagObjectBody(message: String, targetVersion: ReleaseVersion, date:DateTime, commitSha: CommitSha): JsValue = {
    val tagName = "v" + targetVersion.value
    Json.toJson(TagObject(tagName, message, commitSha, Tagger(taggerName, taggerEmail, githubTagDateTimeFormatter.print(date))))
  }

  def buildReleaseBody(message:String, targetVersion:ReleaseVersion):JsValue={
    val tagName = "v" + targetVersion.value

    Json.toJson(
      GitRelease(targetVersion.value, tagName, message, draft = false, prerelease = false))
  }

  def buildMessage(name:String,
                   version:ReleaseVersion,
                   releaserVersion:String,
                   sourceVersion:ReleaseCandidateVersion,
                   artefactMetaData: ArtefactMetaData)={


    s"""
        |Release            : $name ${version.value}
        |Release candidate  : $name ${sourceVersion.value}
        |
        |Last commit sha    : ${artefactMetaData.sha}
        |Last commit author : ${artefactMetaData.commitAuthor}
        |Last commit time   : ${DateTimeFormat.longDateTime().print(artefactMetaData.commitDate)}
        |
        |Release and tag created by [Releaser](https://github.com/hmrc/releaser) $releaserVersion""".stripMargin

  }

  def buildCommitGetUrl(repo:Repo, sha:CommitSha)={
    s"https://api.github.com/repos/hmrc/$repo/git/commits/$sha"
  }
  
  def buildAnnotatedTagRefPostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/$repo/git/refs"
  }

  def buildAnnotatedTagObjectPostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/$repo/git/tags"
  }
  
  def buildReleasePostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/$repo/releases"
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

  def post[A](responseBuilder:(WSResponse) => Try[A])(url:String, body:JsValue): Try[A] = {
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
