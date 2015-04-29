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

import java.nio.file.Paths

import org.joda.time.DateTime
import org.scalatest.{Matchers, TryValues, WordSpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.releaser.domain.ArtefactMetaData

import scala.util.{Try, Success}

class GithubApiSpecs extends WordSpec with Matchers with TryValues{

  import Builders._

  "GithubApiSpecs" should {
    "create the correct url for tagging" in {
      val url = new GithubApi(fakeClockSetToNow()).buildTagPostUrl("myArtefact")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/releases"
    }

    "create the correct url for getting a commit" in {
      val url = new GithubApi(fakeClockSetToNow()).buildCommitGetUrl("myArtefact", "thesha")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/git/commits/thesha"
    }

    "verify the commit" in {
      val clock = fakeClockSetToNow()

      val getResult: Try[Unit] = new GithubApi(clock).verifyCommit((s) => Success())("myArtefact", "thesha")
      getResult shouldBe Success()
    }

    "create the correct body for posting a tag" in {
      val theTime = DateTime.now
      val clock = new Clock{
        override def now(): DateTime = theTime
      }

      val tagDate = DateTime.now()
      val commitDate = DateTime.now().minusDays(4)
      val sourceVersion = "1.0.0-abcd"
      val formatter = GithubApi.githubDateTimeFormatter

      val expectedBody =
        s"""
          |{
          |  "name": "v1.0.1",
          |  "tag_name": "v1.0.1",
          |  "body": "\\n Release and tag created by [Releaser](https://github.com/hmrc/releaser).\\n\\n Release Candidate: 1.0.0-abcd\\n Released from Commit: c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c\\n Last Commit User: charleskubicek\\n Last Commit Time: ${GithubApi.releaseMessageDateTimeFormat.print(commitDate)}",
          |  "target_commitish": "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
          |  "draft" : false,
          |  "prerelease" : false
          |}
        """.stripMargin


      val artefactMetaData = ArtefactMetaData(
        "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
        "charleskubicek",
        commitDate)

      val bodyJson: JsValue = new GithubApi(clock).buildTagBody(sourceVersion, "1.0.1", artefactMetaData)


      Json.prettyPrint(bodyJson) shouldBe Json.prettyPrint(Json.parse(expectedBody))
    }
  }
}
