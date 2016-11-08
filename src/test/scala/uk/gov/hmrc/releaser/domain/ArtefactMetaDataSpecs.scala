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

package uk.gov.hmrc.releaser.domain

import java.nio.file.Paths

import org.scalatest.{Matchers, TryValues, WordSpec}
import uk.gov.hmrc.releaser.GithubApi

import scala.util.Failure

class ArtefactMetaDataSpecs extends WordSpec with Matchers with TryValues{

  "ArtefactMetaData" should {
    "build instance from file" in {
      val md = ArtefactMetaData.fromFile(Paths.get(this.getClass.getResource("/sbt-bobby/sbt-bobby.jar").toURI))  match {
        case Failure(e) => fail(e)
        case s => s
      }

      md.success.value.commitAuthor shouldBe "Charles Kubicek"
      md.success.value.sha  shouldBe "e733d26fa504c040f2c95ecd25a3a55399a00883"
      md.success.value.commitDate shouldBe new GithubApi().githubDateTimeFormatter.parseDateTime("2015-04-09T10:18:12.000Z")
    }
  }
}
