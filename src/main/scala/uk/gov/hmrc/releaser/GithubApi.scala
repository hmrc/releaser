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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{Json, JsValue}

import scala.util.Try

object GithubApi{

  val githubDateTimeFormatter = org.joda.time.format.ISODateTimeFormat.dateTime
  val releaseMessageDateTimeFormat = DateTimeFormat.longDateTime()


  val taggerName  = "hmrc-web-operations"
  val taggerEmail = "hmrc-web-operations@digital.hmrc.gov.uk"

  case class GitRelease(name:String, tag_name:String, body:String, target_commitish:String, draft:Boolean, prerelease:Boolean)
  object GitRelease{
    implicit val formats = Json.format[GitRelease]
  }
}

class GithubApi(clock:Clock){

  import GithubApi._

  val logger = new Logger()

  //TODO how do we test this.
  def postTag(githubConnector: (String, JsValue) => Try[Unit])(a: ArtefactMetaData, v: VersionMapping): Try[Unit] = {
    logger.debug("publishing meta data " + a + " version mapping " + v)
    logger.debug("github url: " + buildUrl(v.artefactName))
    logger.debug("github body: " + buildTagBody(v.sourceVersion, v.targetVersion, a))

    githubConnector(
      buildUrl(v.artefactName),
      buildTagBody(v.sourceVersion, v.targetVersion, a))
  }

  def buildTagBody(sourceVersion:String, targetVersion:String, artefactMd:ArtefactMetaData):JsValue={
    val tagName = "v" + targetVersion
    val message = buildMessage(sourceVersion, artefactMd.sha, artefactMd.commitAuthor, artefactMd.commitDate)

    Json.toJson(
      GitRelease(tagName, tagName, message, artefactMd.sha, draft = false, prerelease = false))
  }

  private def buildMessage(
                            sourceVersion:String,
                            commitSha:String,
                            committerUserName:String,
                            commitDate:DateTime)={
    s"""
        | Release and tag created by [Releaser](https://github.com/hmrc/releaser).
        |
        | Release Candidate: $sourceVersion
        | Released from Commit: $commitSha
        | Last Commit User: $committerUserName
        | Last Commit Time: ${DateTimeFormat.longDateTime().print(commitDate)}""".stripMargin
  }

  def buildUrl(artefactName:String)={
    s"https://api.github.com/repos/hmrc/$artefactName/releases"
  }
}
