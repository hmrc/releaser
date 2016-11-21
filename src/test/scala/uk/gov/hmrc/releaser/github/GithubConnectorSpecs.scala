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

package uk.gov.hmrc.releaser.github

import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec}
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.releaser.Repo
import uk.gov.hmrc.releaser.domain._

import scala.util.Success

class GithubConnectorSpecs extends WordSpec with Matchers with TryValues with OptionValues with MockitoSugar {
  
  val repo = Repo("myRepo")
  val mockHttpConnector = mock[GithubHttp]

  val releaserVersion = "6.6.6"

  val connector = new GithubConnector(
    mockHttpConnector,
    releaserVersion,
    new GithubCommitter("hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk"))

  "GithubConnector" should {

    "Create the github tag object, tag reg and release" in {

      val repoName = "myRepo"
      val artifactName = "myArtefact"
      val rcVersion = "0.9.0-abcdef"
      val releaseVersion = "1.0.0"
      val sha = "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c"
      val author = "charleskubicek"
      val commitDate = new DateTime(2011, 6, 17, 14, 53, 35, UTC)
      val tagDate = new DateTime(2011, 6, 17, 14, 53, 35, UTC)

      val expectedTagObjectBody =
        Json.parse(s"""
           |{
           |  "tag": "v$releaseVersion",
           |  "message": "tag of $releaseVersion",
           |  "object": "$sha",
           |  "tagger": {
           |    "name": "hmrc-web-operations",
           |    "email": "hmrc-web-operations@digital.hmrc.gov.uk",
           |    "date": "2011-06-17T14:53:35Z"
           |  },
           |  "type": "commit"
           |}
        """.stripMargin)

      val expectedTagRefBody =
        Json.parse(s"""
           |{
           |  "ref": "refs/tags/v$releaseVersion",
           |  "sha": "$sha"
           |}
        """.stripMargin)

      val expectedMessage = Json.stringify(new JsString (
        s"""
          |Release            : $artifactName $releaseVersion
          |Release candidate  : $artifactName $rcVersion
          |
          |Last commit sha    : $sha
          |Last commit author : $author
          |Last commit time   : ${GithubConnector.releaseMessageDateTimeFormat.print(commitDate)}
          |
          |Release and tag created by [Releaser](https://github.com/hmrc/releaser) $releaserVersion""".stripMargin))

      val expectedReleaseBody =
        Json.parse(s"""
           |{
           |  "name": "$releaseVersion",
           |  "tag_name": "v$releaseVersion",
           |  "body": $expectedMessage,
           |  "draft" : false,
           |  "prerelease" : false
           |}
        """.stripMargin)

      when(mockHttpConnector.post[CommitSha](any())(meq(s"https://api.github.com/repos/hmrc/$repoName/git/tags"), meq(expectedTagObjectBody)))
        .thenReturn(Success(new CommitSha(sha)))

      when(mockHttpConnector.postUnit(meq(s"https://api.github.com/repos/hmrc/$repoName/git/refs"), meq(expectedTagRefBody)))
        .thenReturn(Success(()))

      when(mockHttpConnector.postUnit(meq(s"https://api.github.com/repos/hmrc/$repoName/releases"), meq(expectedReleaseBody)))
        .thenReturn(Success(()))

      val map = VersionMapping(RepoFlavours.mavenRepository, artifactName, Repo(repoName), ReleaseCandidateVersion(rcVersion), ReleaseVersion(releaseVersion))
      val result = connector.createGithubTagAndRelease(tagDate, sha, author, commitDate, map)

      result shouldBe Success(())
    }

    "Verify that a commit exists" in {
      val repoName = "myRepo"
      val sha = "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c"

      when(mockHttpConnector.get(s"https://api.github.com/repos/hmrc/$repoName/git/commits/$sha")).thenReturn(Success(()))

      val result = connector.verifyGithubTagExists(Repo(repoName), new CommitSha(sha))
      result shouldBe Success(())
    }
  }
}
