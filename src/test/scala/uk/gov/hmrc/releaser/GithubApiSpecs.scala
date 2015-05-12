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
import org.scalatest.{Matchers, TryValues, WordSpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.releaser.domain.ArtefactMetaData

import scala.util.{Success, Try}

class GithubApiSpecs extends WordSpec with Matchers with TryValues{

  "GithubApiSpecs" should {
    "create the correct url for tagging" in {
      val url = GithubApi.buildTagPostUrl("myArtefact")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/releases"
    }

    "create the correct url for getting a commit" in {
      val url = GithubApi.buildCommitGetUrl("myArtefact", "thesha")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/git/commits/thesha"
    }

    "verify the commit" in {
      val getResult: Try[Unit] = GithubApi.verifyCommit((s) => Success(()))("myArtefact", "thesha")
      getResult shouldBe Success(())
    }

    "create the corect message for a tag" in {
      val commitDate = DateTime.now().minusDays(4)

      val sourceVersion = "1.0.0-abcd"
      val artefactMetaData = ArtefactMetaData(
        "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
        "charleskubicek",
        commitDate)

      val expectedMessage =
        s"""
          |Release            : myArtefact 1.0.0
          |Release candidate  : myArtefact 1.0.0-abcd
          |
          |Last commit sha    : c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c
          |Last commit author : charleskubicek
          |Last commit time   : ${GithubApi.releaseMessageDateTimeFormat.print(commitDate)}
          |
          |Release and tag created by [Releaser](https://github.com/hmrc/releaser) 6.6.6""".stripMargin


        GithubApi.buildMessage("myArtefact", "1.0.0", "6.6.6", sourceVersion, artefactMetaData) shouldBe expectedMessage

    }

    "create the correct body for posting a tag" in {

      val commitDate = DateTime.now().minusDays(4)

      val expectedBody =
        s"""
          |{
          |  "name": "1.0.1",
          |  "tag_name": "v1.0.1",
          |  "body": "the message",
          |  "target_commitish": "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
          |  "draft" : false,
          |  "prerelease" : false
          |}
        """.stripMargin


      val artefactMetaData = ArtefactMetaData(
        "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
        "charleskubicek",
        commitDate)

      val bodyJson: JsValue = GithubApi.buildTagBody("the message", "1.0.1", artefactMetaData)


      Json.prettyPrint(bodyJson) shouldBe Json.prettyPrint(Json.parse(expectedBody))
    }
  }
}
