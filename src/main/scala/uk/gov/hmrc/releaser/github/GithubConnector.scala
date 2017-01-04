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

package uk.gov.hmrc.releaser.github

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.{Logger, ServiceCredentials}

import scala.util.{Success, Try}

object GithubConnector extends Logger {
  type GitPost = (String, JsValue) => Try[Unit]
  type GitPostAndGet = (String, JsValue) => Try[CommitSha]

  def apply(githubCreds: ServiceCredentials, releaserVersion : String) = new GithubConnector(new GithubHttp(githubCreds), releaserVersion)

  def dryRun(githubCreds: ServiceCredentials, releaserVersion : String) = {
    log.info("Github : running in dry-run mode")
    new DryRunGithubConnector(releaserVersion)
  }

  val githubDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTime
  val githubTagDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTimeNoMillis
  val releaseMessageDateTimeFormat = DateTimeFormat.longDateTime()
}

case class GithubCommitter(taggerName: String = System.getProperty("github.tagger.name", "hmrc-web-operations"),
                           taggerEmail: String = System.getProperty("github.tagger.email", "hmrc-web-operations@digital.hmrc.gov.uk")) extends Logger {
  log.info(s"Github organisation details : taggerName : '$taggerName', taggerEmail : '$taggerEmail'")
}

class GithubConnector(githubHttp : GithubHttp, releaserVersion : String, comitterDetails : GithubCommitter = GithubCommitter())
  extends GithubTagAndRelease with Logger {

  import GithubConnector._

  case class Tagger(name:String, email:String, date:String)
  case class TagObject(tag:String, message:String, `object`:String, tagger: Tagger, `type`:String = "commit")
  case class TagRef(ref:String, sha:String)
  case class TagRefResponse(sha:String)
  case class GitRelease(name:String, tag_name:String, body:String, draft:Boolean, prerelease:Boolean)

  implicit val taggerFormats = Json.format[Tagger]
  implicit val tagObjectFormats = Json.format[TagObject]
  implicit val tagRefFormats = Json.format[TagRef]
  implicit val tagRefResponseFormats = Json.format[TagRefResponse]
  implicit val gitReleaseFormats = Json.format[GitRelease]

  def verifyGithubTagExists(repo:Repo, sha:CommitSha): Try[Unit] = {
    githubHttp.get(buildCommitGetUrl(repo, sha))
  }

  def createGithubTagAndRelease(tagDate: DateTime, commitSha: CommitSha,
                                 commitAuthor: String, commitDate: DateTime,
                                 artefactName: String, gitRepo: Repo, releaseCandidateVersion: String, version: String): Try[Unit] =
    for (
      tagSha <- createTagObject(tagDate, gitRepo, version, commitSha);
      _ <- createTagRef(gitRepo, version, tagSha);
      _ <- createRelease(commitSha, commitAuthor, commitDate, artefactName, gitRepo, releaseCandidateVersion, version))
      yield ()

  private def createTagObject(tagDate: DateTime, repo:Repo, tag: String, commitSha:CommitSha): Try[CommitSha] = {
    log.debug("creating annotated tag object from " + tag + " version mapping " + tag)

    val url = buildAnnotatedTagObjectPostUrl(repo)
    val body = buildTagObjectBody("tag of " + tag, tag, tagDate, commitSha)

    githubHttp.post[CommitSha]((r:WSResponse) => Success(r.json.as[TagRefResponse].sha))(url, body)
  }

  private def createTagRef(repo:Repo, tag: String, commitSha:CommitSha): Try[Unit] = {
    log.debug("creating annotated tag ref from " + tag + " version mapping " + tag)

    val url = buildAnnotatedTagRefPostUrl(repo)
    val body = buildTagRefBody(tag, commitSha)

    githubHttp.postUnit(url, body)
  }

  private def createRelease(commitSha: CommitSha, commitAuthor: String,
                            commitDate: DateTime, artefactName: String,
                            gitRepo: Repo, releaseCandidateVersion: String, version: String): Try[Unit] = {
    log.debug(s"creating release from $commitSha version " + version)

    val url = buildReleasePostUrl(gitRepo)
    val message = buildMessage(artefactName, version, releaserVersion, releaseCandidateVersion, commitSha, commitAuthor, commitDate)
    val body = buildReleaseBody(message, version)

    githubHttp.postUnit(url, body)
  }

  private def buildTagRefBody(version: String, commitSha: CommitSha): JsValue = {
    val tagName = "refs/tags/v" + version
    Json.toJson(TagRef(tagName, commitSha))
  }

  private def buildTagObjectBody(message: String, version: String, date:DateTime, commitSha: CommitSha): JsValue = {
    val tagName = "v" + version
    Json.toJson(TagObject(tagName, message, commitSha, Tagger(comitterDetails.taggerName, comitterDetails.taggerEmail, githubTagDateTimeFormatter.print(date))))
  }

  private def buildReleaseBody(message:String, version: String): JsValue = {
    val tagName = "v" + version
    Json.toJson(GitRelease(version, tagName, message, draft = false, prerelease = false))
  }

  private def buildMessage(
                    name: String,
                    version: String,
                    releaserVersion: String,
                    releaseCandidateVersion: String,
                    commitSha: CommitSha, commitAuthor: String, commitDate: DateTime) =
    s"""
      |Release            : $name $version
      |Release candidate  : $name $releaseCandidateVersion
      |
      |Last commit sha    : $commitSha
      |Last commit author : $commitAuthor
      |Last commit time   : ${DateTimeFormat.longDateTime().print(commitDate)}
      |
      |Release and tag created by [Releaser](https://github.com/hmrc/releaser) $releaserVersion""".stripMargin

  private def buildCommitGetUrl(repo:Repo, sha:CommitSha)={
    s"https://api.github.com/repos/hmrc/${repo.value}/git/commits/$sha"
  }

  private def buildAnnotatedTagRefPostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/${repo.value}/git/refs"
  }

  private def buildAnnotatedTagObjectPostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/${repo.value}/git/tags"
  }

  private def buildReleasePostUrl(repo:Repo)={
    s"https://api.github.com/repos/hmrc/${repo.value}/releases"
  }
}

class DryRunGithubConnector(releaserVersion: String) extends GithubTagAndRelease {
  val emptyGitPoster: (String, JsValue) => Try[Unit] = (a, b) => { println("Github emptyPost DRY_RUN"); Success(Unit) }
  val emptyGitPosteAndGetter: (String, JsValue) => Try[CommitSha] = (a, b) => { println("Github emptyPost DRY_RUN"); Success("a-fake-tag-sha") }

  override def verifyGithubTagExists(repo: Repo, sha: CommitSha): Try[Unit] = Success(Unit)

  def createGithubTagAndRelease(tagDate: DateTime, commitSha: CommitSha,
    commitAuthor: String, commitDate: DateTime,
    artefactName: String, gitRepo: Repo, releaseCandidateVersion: String, version: String): Try[Unit] = Success(Unit)
}
