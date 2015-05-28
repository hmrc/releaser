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
import org.joda.time.DateTimeZone.UTC
import org.scalatest.{Matchers, TryValues, WordSpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.releaser.domain.ArtefactMetaData

import scala.util.{Success, Try}

class GithubApiSpecs extends WordSpec with Matchers with TryValues{

  "GithubApiSpecs" should {

    "create the correct url for creating a release" in {
      val url = GithubApi.buildReleasePostUrl("myArtefact")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/releases"
    }


    "create the correct url for creating an annotated tag object" in {
      val url = GithubApi.buildAnnotatedTagObjectPostUrl("myArtefact")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/git/tags"
    }

    "create the correct url for creating an annotated tag reference" in {
      val url = GithubApi.buildAnnotatedTagRefPostUrl("myArtefact")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/git/refs"
    }

    "create the correct url for getting a commit" in {
      val url = GithubApi.buildCommitGetUrl("myArtefact", "thesha")

      url shouldBe "https://api.github.com/repos/hmrc/myArtefact/git/commits/thesha"
    }

    "verify the commit" in {
      val getResult: Try[Unit] = GithubApi.verifyCommit((s) => Success(()))("myArtefact", "thesha")
      getResult shouldBe Success(())
    }

    "create the correct message for a release" in {
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

    "create the correct body for creating an annotated tag object" in {

      val tagDate = new DateTime(2011, 6, 17, 14, 53, 35, UTC)

      val expectedBody =
        s"""
           |{
           |  "tag": "v1.0.1",
           |  "message": "creating an annotated tag",
           |  "object": "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c",
           |  "tagger": {
           |    "name": "hmrc-web-operations",
           |    "email": "hmrc-web-operations@digital.hmrc.gov.uk",
           |    "date": "2011-06-17T14:53:35Z"
           |  },
           |  "type": "commit"
           |}
        """.stripMargin

      val bodyJson: JsValue = GithubApi.buildTagObjectBody("creating an annotated tag", "1.0.1", tagDate, "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c")


      Json.prettyPrint(bodyJson) shouldBe Json.prettyPrint(Json.parse(expectedBody))
    }
    "create the correct body for creating an annotated tag reference" in {

      val expectedBody =
        s"""
           |{
           |  "ref": "refs/tags/v1.0.1",
           |  "sha": "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c"
           |}
        """.stripMargin

      val bodyJson: JsValue = GithubApi.buildTagRefBody("1.0.1", "c3d0be41ecbe669545ee3e94d31ed9a4bc91ee3c")


      Json.prettyPrint(bodyJson) shouldBe Json.prettyPrint(Json.parse(expectedBody))
    }

    "create the correct body for creating a release" in {

      val commitDate = DateTime.now().minusDays(4)

      val expectedBody =
        s"""
          |{
          |  "name": "1.0.1",
          |  "tag_name": "v1.0.1",
          |  "body": "the message",
          |  "draft" : false,
          |  "prerelease" : false
          |}
        """.stripMargin

      val bodyJson: JsValue = GithubApi.buildReleaseBody("the message", "1.0.1")


      Json.prettyPrint(bodyJson) shouldBe Json.prettyPrint(Json.parse(expectedBody))
    }
  }
}
